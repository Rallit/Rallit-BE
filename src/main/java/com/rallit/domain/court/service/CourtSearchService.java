package com.rallit.domain.court.service;

import java.math.BigDecimal;
import java.util.function.Supplier;

import com.rallit.domain.court.client.KakaoLocalClient;
import com.rallit.domain.court.dto.CourtPlaceResponse;
import com.rallit.domain.court.dto.CourtRegisterRequest;
import com.rallit.domain.court.dto.CourtResponse;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.enums.CourtSearchSort;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtConstraintViolations;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CourtSearchService {

	public static final int DEFAULT_RADIUS_METERS = 5_000;
	public static final int MAX_PAGE = 44;
	static final String NEARBY_KEYWORD = "테니스장";
	private static final int VERIFICATION_RADIUS_METERS = 100;
	private static final int VERIFICATION_PAGE_SIZE = 15;
	private static final int DISTRICT_CODE_LENGTH = 5;

	private final KakaoLocalClient kakao;
	private final CourtSearchRateLimiter rateLimiter;
	private final CourtRepository courts;
	private final AreaRepository areas;

	public PageResponse<CourtPlaceResponse> search(Long userId, String keyword, BigDecimal latitude,
			BigDecimal longitude, Integer radiusMeters, CourtSearchSort sort, int page, int size) {
		if ((latitude == null) != (longitude == null)) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_LOCATION_REQUIRED);
		}
		boolean located = latitude != null;
		if (!located && (radiusMeters != null || sort == CourtSearchSort.DISTANCE)) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_LOCATION_REQUIRED);
		}
		if (!StringUtils.hasText(keyword) && !located) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_KEYWORD_REQUIRED);
		}
		String query = StringUtils.hasText(keyword) ? keyword.strip() : NEARBY_KEYWORD;
		boolean byDistance = sort == null ? located : sort == CourtSearchSort.DISTANCE;
		Integer radius = located ? (radiusMeters == null ? DEFAULT_RADIUS_METERS : radiusMeters) : null;
		rateLimiter.acquire(userId);
		var result = request(new KakaoLocalClient.KeywordSearch(query, latitude, longitude, radius, byDistance,
				page + 1, size));
		return new PageResponse<>(result.places().stream().map(CourtPlaceResponse::from).toList(), page, size,
				page < MAX_PAGE && !result.end(), result.pageableCount());
	}

	public Registration register(Long userId, CourtRegisterRequest request) {
		var existing = courts.findByKakaoPlaceId(request.kakaoPlaceId());
		if (existing.isPresent()) {
			return new Registration(CourtResponse.from(existing.get()), false);
		}
		rateLimiter.acquire(userId);
		var place = request(new KakaoLocalClient.KeywordSearch(request.name().strip(), request.latitude(),
				request.longitude(), VERIFICATION_RADIUS_METERS, true, 1, VERIFICATION_PAGE_SIZE)).places().stream()
				.filter(candidate -> candidate.id().equals(request.kakaoPlaceId()))
				.findFirst()
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_PLACE_NOT_VERIFIED));
		Area area = districtOf(place);
		try {
			var court = courts.saveAndFlush(Court.builder()
					.name(place.name())
					.address(place.address())
					.latitude(place.latitude())
					.longitude(place.longitude())
					.area(area)
					.kakaoPlaceId(place.id())
					.build());
			return new Registration(CourtResponse.from(court), true);
		} catch (DataIntegrityViolationException failure) {
			if (!CourtConstraintViolations.isKakaoPlaceConflict(failure)) {
				throw failure;
			}
			return new Registration(CourtResponse.from(courts.findByKakaoPlaceId(place.id())
					.orElseThrow(() -> failure)), false);
		}
	}

	private Area districtOf(KakaoLocalClient.Place place) {
		return request(() -> kakao.legalDistrictCode(place.latitude(), place.longitude()))
				.filter(code -> code.length() >= DISTRICT_CODE_LENGTH)
				.flatMap(code -> areas.findByCode(code.substring(0, DISTRICT_CODE_LENGTH)))
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_UNSUPPORTED_AREA));
	}

	private KakaoLocalClient.PlacePage request(KakaoLocalClient.KeywordSearch search) {
		return request(() -> kakao.searchKeyword(search));
	}

	private static <T> T request(Supplier<T> call) {
		try {
			return call.get();
		} catch (KakaoLocalClient.UnavailableException failure) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_UNAVAILABLE, failure);
		}
	}

	public record Registration(CourtResponse court, boolean created) {
	}
}
