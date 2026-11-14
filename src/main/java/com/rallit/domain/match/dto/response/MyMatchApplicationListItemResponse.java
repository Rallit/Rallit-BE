package com.rallit.domain.match.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;

public record MyMatchApplicationListItemResponse(Long applicationId, MatchApplicationStatus applicationStatus,
		LocalDateTime appliedAt, Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes, LocalDateTime startsAt,
		LocalDateTime endsAt, String courtName, String courtAddress, String courtNumber,
		Integer participationFee, int currentParticipantCount, int capacity) {

	public static MyMatchApplicationListItemResponse of(MatchApplication application, Map<Long, Long> occupiedSeatCounts) {
		Match match = application.getMatch();
		CourtSlot courtSlot = match.getCourtSlot();
		Court court = courtSlot.getCourt();
		int currentParticipantCount = 1 + occupiedSeatCounts.getOrDefault(match.getId(), 0L).intValue();
		int capacity = 1 + match.getMaleRecruitCount() + match.getFemaleRecruitCount();
		return new MyMatchApplicationListItemResponse(application.getId(), application.getStatus(), application.getCreatedAt(),
				match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
				courtSlot.getStartsAt(), courtSlot.getEndsAt(), court.getName(), court.getAddress(),
				courtSlot.getCourtNumber(), match.getParticipationFee(), currentParticipantCount, capacity);
	}
}
