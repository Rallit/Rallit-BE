package com.rallit.domain.match.dto.response;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationRejectReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationRejectResponse(Long applicationId, MatchApplicationStatus status,
		MatchApplicationRejectReason rejectReason) {

	public static MatchApplicationRejectResponse from(MatchApplication application) {
		return new MatchApplicationRejectResponse(application.getId(), application.getStatus(),
				application.getRejectReason());
	}
}
