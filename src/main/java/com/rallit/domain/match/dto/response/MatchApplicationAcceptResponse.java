package com.rallit.domain.match.dto.response;

import java.time.LocalDateTime;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationAcceptResponse(Long applicationId, MatchApplicationStatus status,
		LocalDateTime acceptedAt, LocalDateTime paymentDueAt) {

	public static MatchApplicationAcceptResponse from(MatchApplication application) {
		return new MatchApplicationAcceptResponse(application.getId(), application.getStatus(),
				application.getAcceptedAt(), application.getPaymentDueAt());
	}
}
