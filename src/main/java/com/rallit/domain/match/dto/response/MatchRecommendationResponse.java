package com.rallit.domain.match.dto.response;

import java.time.ZoneId;

import com.rallit.domain.match.entity.Match;

public record MatchRecommendationResponse(MatchListItemResponse match, String areaCode, String areaName) {

	public static MatchRecommendationResponse from(Match match, long occupiedSeats, ZoneId zone) {
		var area = match.getCourtSlot().getCourt().getArea();
		return new MatchRecommendationResponse(MatchListItemResponse.from(match, occupiedSeats, zone),
				area.getCode(), area.getName());
	}
}
