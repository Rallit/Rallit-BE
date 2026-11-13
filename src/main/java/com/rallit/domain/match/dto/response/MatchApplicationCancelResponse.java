package com.rallit.domain.match.dto.response;

import java.time.LocalDateTime;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationCancelResponse(Long applicationId, MatchApplicationStatus status,
		LocalDateTime canceledAt, MatchApplicationCancelReason cancelReason) {

	public static MatchApplicationCancelResponse from(MatchApplication application) {
		return new MatchApplicationCancelResponse(application.getId(), application.getStatus(),
				application.getCanceledAt(), application.getCancelReason());
	}
}
