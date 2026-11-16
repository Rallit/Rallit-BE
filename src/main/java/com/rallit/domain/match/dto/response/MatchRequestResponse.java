package com.rallit.domain.match.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.rallit.domain.match.entity.MatchRequest;
import com.rallit.domain.match.entity.MatchRequestConditions;

public record MatchRequestResponse(Long requestId, MatchRequestConditions conditions, OffsetDateTime expiresAt) {

	public static MatchRequestResponse from(MatchRequest request, ZoneId zone) {
		return new MatchRequestResponse(request.getId(), request.getConditions(),
				request.getExpiresAt().atZone(zone).toOffsetDateTime());
	}
}
