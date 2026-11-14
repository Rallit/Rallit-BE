package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.match.dto.response.MatchDetailResponse;
import com.rallit.domain.match.dto.response.MatchListResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchApplicationRepository.MatchApplicationCount;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchDiscoveryService {

	private final MatchRepository matches;
	private final MatchApplicationRepository applications;
	private final MatchApplicationEligibility eligibility;
	private final UserRepository users;
	private final Clock clock;

	public MatchListResponse list(LocalDate date, LocalTime startTimeFrom, LocalTime startTimeTo,
			List<MatchType> matchTypes, int page, int size) {
		if ((date == null && (startTimeFrom != null || startTimeTo != null))
				|| (startTimeFrom != null && startTimeTo != null && !startTimeFrom.isBefore(startTimeTo))) {
			throw new GeneralException(ErrorStatus._BAD_REQUEST);
		}
		var from = date == null ? null : date.atTime(startTimeFrom == null ? LocalTime.MIDNIGHT : startTimeFrom);
		var before = date == null ? null : date.plusDays(1).atStartOfDay();
		var endsBy = startTimeTo == null ? null : date.atTime(startTimeTo);
		boolean allTypes = matchTypes == null || matchTypes.isEmpty();
		var types = allTypes ? EnumSet.allOf(MatchType.class) : EnumSet.copyOf(matchTypes);
		var result = matches.findRecruitingMatches(CourtSlotStatus.APPROVED, MatchStatus.RECRUITING, now(), from,
				before, endsBy, allTypes, types, PageRequest.of(page, size));
		var counts = occupiedSeats(result.stream().map(Match::getId).toList());
		var cards = result.stream().map(match -> MatchListResponse.Item.from(match,
				counts.getOrDefault(match.getId(), 0L), clock.getZone())).toList();
		return new MatchListResponse(cards, result.getNumber(), result.getSize(), result.hasNext(), result.getTotalElements());
	}

	public MatchDetailResponse detail(Long matchId, Long userId) {
		var now = now();
		var match = matches.findDiscoveryDetail(matchId, CourtSlotStatus.APPROVED, MatchStatus.RECRUITING, now)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_NOT_FOUND));
		var applicant = users.findById(userId).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
		var reason = eligibility.blockReason(match, applicant, now);
		long count = occupiedSeats(List.of(matchId)).getOrDefault(matchId, 0L);
		return MatchDetailResponse.from(match, count, clock.getZone(),
				MatchApplicationEligibility.isWithinRecruitmentWindow(match, now), reason.orElse(null));
	}

	private Map<Long, Long> occupiedSeats(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return applications.countByMatchIdsAndStatuses(ids, MatchApplicationStatus.SEAT_OCCUPYING).stream()
				.collect(Collectors.toMap(MatchApplicationCount::getMatchId, MatchApplicationCount::getCount));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
