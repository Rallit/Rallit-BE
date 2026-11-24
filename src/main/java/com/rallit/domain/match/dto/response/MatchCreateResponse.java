package com.rallit.domain.match.dto.response;

import java.time.Instant;

import com.rallit.domain.court.enums.EvidenceReviewStatus;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchCreateResponse(Long matchId,
		@Schema(description = "개설 즉시 RECRUITING입니다.") MatchStatus matchStatus,
		Long courtId, Long courtSlotId, Instant startsAt, Instant endsAt,
		@Schema(description = "모집 마감. 기존 신청 기한과 같은 경기 시작 1시간 전입니다.") Instant recruitmentDeadline,
		@Schema(description = "증빙을 첨부하면 PENDING, 생략하면 NOT_SUBMITTED입니다.") EvidenceReviewStatus evidenceReviewStatus,
		@Schema(description = "운영자가 증빙을 승인했으면 true") boolean evidenceVerified,
		Instant createdAt) {

	public static MatchCreateResponse from(Match match) {
		var slot = match.getCourtSlot();
		return new MatchCreateResponse(match.getId(), match.getStatus(), slot.getCourt().getId(), slot.getId(),
				slot.getStartsAt(), slot.getEndsAt(), match.getRecruitmentDeadline(), slot.getEvidenceReviewStatus(),
				slot.isEvidenceVerified(), match.getCreatedAt());
	}
}
