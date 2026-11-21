package com.rallit.domain.match.dto.response;

import java.time.Instant;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationCreateResponse(Long applicationId, Long matchId, MatchApplicationStatus status,
		Instant appliedAt) {

	public static MatchApplicationCreateResponse from(MatchApplication application) {
		return new MatchApplicationCreateResponse(application.getId(), application.getMatch().getId(),
				application.getStatus(), application.getCreatedAt());
	}
}
