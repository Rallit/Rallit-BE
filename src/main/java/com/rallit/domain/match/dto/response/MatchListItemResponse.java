package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchListItemResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		OffsetDateTime startsAt, OffsetDateTime endsAt, String courtName, Integer participationFee,
		@Schema(description = "모집 NTRP 하한. 미설정이면 null", types = {"number", "null"}) BigDecimal minNtrp,
		@Schema(description = "모집 NTRP 상한. 미설정이면 null", types = {"number", "null"}) BigDecimal maxNtrp,
		@Schema(description = "호스트와 결제 대기·참가 확정 인원을 합한 수") long currentParticipantCount,
		@Schema(description = "호스트 1명과 남녀 모집 정원을 합한 수") int capacity) {

	public static MatchListItemResponse from(Match match, long occupiedSeats, ZoneId zone) {
		var slot = match.getCourtSlot();
		return new MatchListItemResponse(match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
				slot.getStartsAt().atZone(zone).toOffsetDateTime(), slot.getEndsAt().atZone(zone).toOffsetDateTime(),
				slot.getCourt().getName(), match.getParticipationFee(), match.getMinNtrp(), match.getMaxNtrp(),
				1 + occupiedSeats, 1 + match.getMaleRecruitCount() + match.getFemaleRecruitCount());
	}
}
