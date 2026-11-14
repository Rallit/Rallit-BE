package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;

public record MatchListItemResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		OffsetDateTime startsAt, OffsetDateTime endsAt, String courtName, Integer participationFee,
		BigDecimal minNtrp, BigDecimal maxNtrp, long currentParticipantCount, int capacity) {

	public static MatchListItemResponse from(Match match, long occupiedSeats, ZoneId zone) {
		var slot = match.getCourtSlot();
		return new MatchListItemResponse(match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
				slot.getStartsAt().atZone(zone).toOffsetDateTime(), slot.getEndsAt().atZone(zone).toOffsetDateTime(),
				slot.getCourt().getName(), match.getParticipationFee(), match.getMinNtrp(), match.getMaxNtrp(),
				1 + occupiedSeats, 1 + match.getMaleRecruitCount() + match.getFemaleRecruitCount());
	}
}
