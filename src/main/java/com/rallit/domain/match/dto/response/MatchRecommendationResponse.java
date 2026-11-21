package com.rallit.domain.match.dto.response;

import com.rallit.domain.match.entity.Match;

public record MatchRecommendationResponse(MatchListItemResponse match, String areaCode, String areaName) {

	public static MatchRecommendationResponse from(Match match, long occupiedSeats) {
		var area = match.getCourtSlot().getCourt().getArea();
		return new MatchRecommendationResponse(MatchListItemResponse.from(match, occupiedSeats),
				area.getCode(), area.getName());
	}
}
