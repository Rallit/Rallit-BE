package com.rallit.domain.match.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRecommendationResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchApplicationRepository.MatchApplicationCount;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchRecommendationService {

	private final MatchRepository matches;
	private final MatchApplicationRepository applications;
	private final UserRepository users;
	private final AreaRepository areas;
	private final Clock clock;

	public PageResponse<MatchRecommendationResponse> home(Long userId, int page, int size) {
		User user = onboardedUser(userId);
		var areaCodes = user.getActivityAreas().stream().map(Area::getCode).toList();
		var timeSlots = user.getPreferredTimeSlots().stream().map(Enum::name).toList();
		var types = matchTypes(user.getPreferredMatchTypes());
		var pageable = PageRequest.of(page, size);
		Page<Match> result;
		if (areaCodes.isEmpty() || timeSlots.isEmpty() || types.isEmpty()) {
			result = Page.empty(pageable);
		} else {
			result = matches.findHomeRecommendations(CourtSlotStatus.APPROVED, MatchStatus.RECRUITING, now(),
					false, types, userId, user.getGender(), MatchApplicationStatus.ACTIVE, user.isSameGenderOnly(),
					areaCodes, timeSlots, user.getNtrp(), pageable);
		}
		MatchPageRange.requireInRange(result);
		var counts = occupiedSeats(result.getContent());
		return PageResponse.from(result.map(match -> view(match, counts)));
	}

	public MatchSearchResponse search(Long userId, MatchSearchRequest request, int exactPage, int similarPage,
			int size) {
		User user = onboardedUser(userId);
		var window = validateTimeWindow(request);
		var areaCodes = new HashSet<>(request.areaCodes());
		var selectedAreas = areas.findAllByCodeIn(areaCodes);
		if (selectedAreas.size() != areaCodes.size()) {
			throw new GeneralException(MatchErrorStatus.MATCH_INVALID_SEARCH_AREA);
		}
		var candidates = findSearchCandidates(user, request, window);
		var results = MatchSearchRules.group(candidates, areaCodes, selectedAreas, window);
		var exact = page(results.exact(), exactPage, size);
		var similar = page(results.similar(), similarPage, size);
		MatchPageRange.requireInRange(exact);
		MatchPageRange.requireInRange(similar);
		var counts = occupiedSeats(Stream.concat(exact.stream(), similar.stream()).toList());
		return new MatchSearchResponse(!results.exact().isEmpty() || !results.similar().isEmpty(),
				PageResponse.from(exact.map(match -> view(match, counts))),
				PageResponse.from(similar.map(match -> view(match, counts))), results.relaxedConditions());
	}

	private static MatchSearchRules.TimeWindow validateTimeWindow(MatchSearchRequest request) {
		LocalDate date;
		try {
			date = LocalDate.parse(request.date());
		} catch (DateTimeParseException e) {
			throw new GeneralException(ErrorStatus._BAD_REQUEST, e);
		}
		if (date.getYear() < 1 || date.getYear() > 9999) {
			throw new GeneralException(ErrorStatus._BAD_REQUEST);
		}
		var window = MatchSearchRules.TimeWindow.of(date, request.startTimeFrom(), request.startTimeTo());
		if (!window.from().isBefore(window.to())) {
			throw new GeneralException(MatchErrorStatus.MATCH_INVALID_TIME_RANGE);
		}
		return window;
	}

	private List<Match> findSearchCandidates(User user, MatchSearchRequest request, MatchSearchRules.TimeWindow window) {
		var levels = request.ntrp() == null ? List.<BigDecimal>of() : request.ntrp().stream()
				.map(value -> new BigDecimal(value.replace("+", ""))).sorted(Comparator.naturalOrder()).toList();
		var types = matchTypes(request.matchTypes());
		boolean allTypes = types.isEmpty();
		return matches.findSearchCandidates(CourtSlotStatus.APPROVED, MatchStatus.RECRUITING, now(),
				window.expandedFrom(), window.dayEnd(), window.expandedTo(), allTypes,
				allTypes ? EnumSet.allOf(MatchType.class) : types, user.getId(), user.getGender(), MatchApplicationStatus.ACTIVE,
				Boolean.TRUE.equals(request.sameGenderOnly()), !levels.isEmpty(),
				levels.isEmpty() ? BigDecimal.ZERO : levels.getFirst(),
				levels.isEmpty() ? BigDecimal.ZERO : levels.getLast());
	}

	private static EnumSet<MatchType> matchTypes(Iterable<PreferredMatchType> preferences) {
		var types = EnumSet.noneOf(MatchType.class);
		if (preferences != null) {
			for (PreferredMatchType preference : preferences) {
				switch (preference) {
					case SINGLES -> types.add(MatchType.SINGLES);
					case RALLY -> types.add(MatchType.RALLY);
					case MIXED_DOUBLES -> types.add(MatchType.MIXED_DOUBLES);
					case DOUBLES -> {
						types.add(MatchType.MENS_DOUBLES);
						types.add(MatchType.WOMENS_DOUBLES);
					}
				}
			}
		}
		return types;
	}

	private User onboardedUser(Long userId) {
		var user = users.findById(userId).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
		if (!user.isOnboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_ONBOARDING_REQUIRED);
		}
		return user;
	}

	private static Page<Match> page(List<Match> items, int page, int size) {
		var pageable = PageRequest.of(page, size);
		int start = (int) Math.min(items.size(), pageable.getOffset());
		int end = Math.min(items.size(), start + size);
		return new PageImpl<>(items.subList(start, end), pageable, items.size());
	}

	private Map<Long, Long> occupiedSeats(List<Match> page) {
		if (page.isEmpty()) {
			return Map.of();
		}
		var ids = page.stream().map(Match::getId).toList();
		return applications.countByMatchIdsAndStatuses(ids, MatchApplicationStatus.SEAT_OCCUPYING).stream()
				.collect(Collectors.toMap(MatchApplicationCount::getMatchId, MatchApplicationCount::getCount));
	}

	private MatchRecommendationResponse view(Match match, Map<Long, Long> counts) {
		return MatchRecommendationResponse.from(match, counts.getOrDefault(match.getId(), 0L), clock.getZone());
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
