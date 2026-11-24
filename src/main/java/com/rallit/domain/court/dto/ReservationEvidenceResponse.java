package com.rallit.domain.court.dto;

import java.time.Instant;

import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.enums.EvidenceReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationEvidenceResponse(
		@Schema(description = "예약 증빙 제출 여부") boolean submitted,
		@Schema(description = "NOT_SUBMITTED 미제출(미인증), PENDING 검수 대기, APPROVED 승인, REJECTED 반려. "
				+ "검수 상태와 관계없이 매치 모집은 계속됩니다.") EvidenceReviewStatus reviewStatus,
		@Schema(description = "운영자가 증빙을 승인했으면 true") boolean verified,
		@Schema(description = "검수 완료 시각. 검수 전이면 null", types = {"string", "null"}) Instant reviewedAt,
		@Schema(description = "반려 사유. 반려가 아니면 null", types = {"string", "null"}) String rejectionReason) {

	public static ReservationEvidenceResponse from(CourtSlot slot) {
		return new ReservationEvidenceResponse(slot.getEvidenceFileKey() != null, slot.getEvidenceReviewStatus(),
				slot.isEvidenceVerified(), slot.getReviewedAt(), slot.getRejectionReason());
	}
}
