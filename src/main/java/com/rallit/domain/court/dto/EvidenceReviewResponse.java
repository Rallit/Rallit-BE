package com.rallit.domain.court.dto;

import com.rallit.domain.court.entity.CourtSlot;

public record EvidenceReviewResponse(Long courtSlotId, ReservationEvidenceResponse evidence) {

	public static EvidenceReviewResponse from(CourtSlot slot) {
		return new EvidenceReviewResponse(slot.getId(), ReservationEvidenceResponse.from(slot));
	}
}
