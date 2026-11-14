package com.rallit.domain.match.dto.response;

import java.time.LocalDateTime;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;

public record MatchApplicationCreateResponse(Long applicationId, Long matchId, MatchApplicationStatus status,
		LocalDateTime appliedAt) {

	public static MatchApplicationCreateResponse from(MatchApplication application) {
		return new MatchApplicationCreateResponse(application.getId(), application.getMatch().getId(),
				application.getStatus(), application.getCreatedAt());
	}
}
