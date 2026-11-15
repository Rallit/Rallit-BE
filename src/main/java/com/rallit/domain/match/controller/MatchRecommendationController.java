package com.rallit.domain.match.controller;

import com.rallit.domain.match.dto.response.MatchRecommendationResponse;
import com.rallit.domain.match.service.MatchRecommendationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/matches", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "매치 추천")
@SecurityRequirement(name = "bearerAuth")
@MatchReadErrorResponses
public class MatchRecommendationController {

	private static final String PAGE_DESCRIPTION = "0부터 시작하는 페이지. 첫 페이지 이후 결과 범위를 벗어나면 400입니다.";

	private final MatchRecommendationService recommendations;

	@GetMapping("/recommendations")
	@Operation(summary = "온보딩 기반 홈 매치 추천",
			description = "활동 지역, 선호 경기 방식과 시간대가 맞는 신청 가능 매치를 추천합니다. 모집 NTRP 범위에 "
					+ "회원의 등급이 들어오는 매치를 먼저 표시합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
			description = "홈 추천 조회 성공. 첫 페이지에 결과가 없으면 빈 목록을 반환합니다.",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "페이지 또는 페이지 크기가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
							@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
							@ExampleObject(name = "페이지 범위 초과", value = MatchDiscoveryExamples.PAGE_OUT_OF_RANGE)
					}))
	public ResponseEntity<ApiResponse<PageResponse<MatchRecommendationResponse>>> home(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = PAGE_DESCRIPTION)
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(recommendations.home(principal.userId(), page, size)));
	}
}
