package com.rallit.domain.match.controller;

import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRecommendationResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
					+ "회원의 등급이 들어오는 매치를 먼저 표시합니다. 설정된 NTRP 경계만 비교하고, 양쪽 경계가 모두 "
					+ "미설정인 매치는 우선 정렬에서 제외합니다.")
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

	@GetMapping("/search")
	@Operation(summary = "조건에 맞는 매치와 비슷한 매치 검색",
			description = "정확히 일치하는 결과와 비슷한 매치를 각각 반환합니다. 비슷한 매치는 지역만, 시간만, "
					+ "지역과 시간 모두를 완화하는 순으로 찾아 최초로 결과가 나온 단계만 반환합니다. 지역은 "
					+ "선택한 자치구 대표점에서 직선거리 5km 이하, 시간은 각 선택일 안에서 앞뒤 1시간을 적용합니다. "
					+ "여러 날짜를 선택해도 시간대는 공통으로 적용합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
			description = "정확 일치와 비슷한 매치 조회 성공. 각 목록의 첫 페이지에 결과가 없으면 빈 목록을 반환합니다.",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "요청 형식, 자치구, 시간 범위, NTRP 범위 또는 페이지가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
							@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
							@ExampleObject(name = "지원하지 않는 자치구", value = MatchDiscoveryExamples.INVALID_SEARCH_AREA),
							@ExampleObject(name = "잘못된 시간 범위", value = MatchDiscoveryExamples.INVALID_TIME_RANGE),
							@ExampleObject(name = "잘못된 NTRP 범위", value = MatchDiscoveryExamples.INVALID_SEARCH_NTRP),
							@ExampleObject(name = "페이지 범위 초과", value = MatchDiscoveryExamples.PAGE_OUT_OF_RANGE)
					}))
	public ResponseEntity<ApiResponse<MatchSearchResponse>> search(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Valid @ModelAttribute @ParameterObject MatchSearchRequest request,
			@Parameter(description = "정확히 일치한 목록. " + PAGE_DESCRIPTION)
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int exactPage,
			@Parameter(description = "비슷한 매치 목록. " + PAGE_DESCRIPTION)
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int similarPage,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				recommendations.search(principal.userId(), request, exactPage, similarPage, size)));
	}
}
