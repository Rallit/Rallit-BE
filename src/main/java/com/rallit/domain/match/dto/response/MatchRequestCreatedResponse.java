package com.rallit.domain.match.dto.response;

public record MatchRequestCreatedResponse(MatchRequestResponse request, long exactMatchCount, boolean hasResults) {
}
