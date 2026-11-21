package com.rallit.domain.match.dto.response;

import java.time.Instant;

import com.rallit.domain.match.entity.MatchRequest;
import com.rallit.domain.match.entity.MatchRequestConditions;

public record MatchRequestResponse(Long requestId, MatchRequestConditions conditions, Instant expiresAt) {

	public static MatchRequestResponse from(MatchRequest request) {
		return new MatchRequestResponse(request.getId(), request.getConditions(), request.getExpiresAt());
	}
}
