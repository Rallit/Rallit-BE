package com.rallit.domain.match.dto.response;

import java.util.List;

import com.rallit.global.apiPayload.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchSearchResponse(
		@Schema(description = "두 목록 중 하나에라도 결과가 있으면 true")
		boolean hasResults,
		PageResponse<MatchRecommendationResponse> exactMatches,
		PageResponse<MatchRecommendationResponse> similarMatches,
		@Schema(description = "비슷한 매치에 적용한 완화 조건. 비슷한 매치가 없으면 빈 배열")
		List<RelaxedCondition> relaxedConditions) {

	public enum RelaxedCondition {
		AREA,
		TIME
	}
}
