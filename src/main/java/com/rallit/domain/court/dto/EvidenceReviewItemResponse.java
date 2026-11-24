package com.rallit.domain.court.dto;

import java.time.Instant;

import com.rallit.domain.court.entity.CourtSlot;
import io.swagger.v3.oas.annotations.media.Schema;

public record EvidenceReviewItemResponse(Long courtSlotId, Long courtId, String courtName, String courtAddress,
		String courtNumber, Instant startsAt, Instant endsAt,
		@Schema(description = "예약을 등록한 호스트") Registrant registrant,
		@Schema(description = "예약 정보와 증빙을 제출한 시각") Instant submittedAt,
		ReservationEvidenceResponse evidence) {

	public static EvidenceReviewItemResponse from(CourtSlot slot) {
		var court = slot.getCourt();
		var registrant = slot.getRegistrant();
		return new EvidenceReviewItemResponse(slot.getId(), court.getId(), court.getName(), court.getAddress(),
				slot.getCourtNumber(), slot.getStartsAt(), slot.getEndsAt(),
				new Registrant(registrant.getId(), registrant.getNickname()), slot.getCreatedAt(),
				ReservationEvidenceResponse.from(slot));
	}

	public record Registrant(Long userId, String nickname) {
	}
}
