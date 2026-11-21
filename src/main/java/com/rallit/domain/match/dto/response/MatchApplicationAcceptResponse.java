package com.rallit.domain.match.dto.response;

import java.time.Instant;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationAcceptResponse(Long applicationId, MatchApplicationStatus status,
		Instant acceptedAt, Instant paymentDueAt) {

	public static MatchApplicationAcceptResponse from(MatchApplication application) {
		return new MatchApplicationAcceptResponse(application.getId(), application.getStatus(),
				application.getAcceptedAt(), application.getPaymentDueAt());
	}
}
