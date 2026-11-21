package com.rallit.domain.match.dto.response;

import java.time.Instant;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationCancelResponse(Long applicationId, MatchApplicationStatus status,
		Instant canceledAt, MatchApplicationCancelReason cancelReason) {

	public static MatchApplicationCancelResponse from(MatchApplication application) {
		return new MatchApplicationCancelResponse(application.getId(), application.getStatus(),
				application.getCanceledAt(), application.getCancelReason());
	}
}
