package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchListItemResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		Instant startsAt, Instant endsAt, String courtName,
		@Schema(description = CourtThumbnailResponse.URL_DESCRIPTION + " 등록된 사진이 없으면 null입니다.",
				types = {"string", "null"}) String courtThumbnailUrl,
		Integer participationFee,
		@Schema(description = "모집 NTRP 하한. 미설정이면 null", types = {"number", "null"}) BigDecimal minNtrp,
		@Schema(description = "모집 NTRP 상한. 미설정이면 null", types = {"number", "null"}) BigDecimal maxNtrp,
		@Schema(description = "호스트와 결제 대기·참가 확정 인원을 합한 수") long currentParticipantCount,
		@Schema(description = "호스트 1명과 남녀 모집 정원을 합한 수") int capacity,
		@Schema(description = "운영자가 코트 예약 증빙을 승인했으면 true. 증빙 미제출·검수 대기·반려는 false")
		boolean evidenceVerified) {

	public static MatchListItemResponse from(Match match, long occupiedSeats) {
		var slot = match.getCourtSlot();
		var court = slot.getCourt();
		String thumbnailUrl = court.getThumbnailImageKey() == null ? null : CourtThumbnailResponse.urlFor(court.getId());
		return new MatchListItemResponse(match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
				slot.getStartsAt(), slot.getEndsAt(),
				court.getName(), thumbnailUrl, match.getParticipationFee(), match.getMinNtrp(), match.getMaxNtrp(),
				1 + occupiedSeats, 1 + match.getMaleRecruitCount() + match.getFemaleRecruitCount(),
				slot.isEvidenceVerified());
	}
}
