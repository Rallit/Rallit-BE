package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.match.dto.response.MatchRecommendationResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchApplicationRepository.MatchApplicationCount;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
