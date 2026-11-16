package com.rallit.domain.match.controller;

import java.net.URI;
import java.util.List;

import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRequestCreatedResponse;
import com.rallit.domain.match.dto.response.MatchRequestResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
import com.rallit.domain.match.service.MatchRequestService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
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
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/match-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "매칭 요청")
@SecurityRequirement(name = "bearerAuth")
@MatchReadErrorResponses
public class MatchRequestController {

	private static final String INVALID_DATE =
			"{\"isSuccess\":false,\"code\":\"MATCH_REQUEST_INVALID_DATE\",\"message\":\"매칭 요청 날짜는 오늘부터 14일 이내로 선택해 주세요.\"}";
	private static final String NOT_FOUND =
			"{\"isSuccess\":false,\"code\":\"MATCH_REQUEST_NOT_FOUND\",\"message\":\"매칭 요청을 찾을 수 없습니다.\"}";
	private static final String LIMIT_REACHED =
			"{\"isSuccess\":false,\"code\":\"MATCH_REQUEST_LIMIT_REACHED\",\"message\":\"활성 매칭 요청을 최대 3개까지 저장할 수 있습니다.\"}";

	private final MatchRequestService requests;

	@PostMapping
	@Operation(summary = "매칭 요청 저장", description = "조건을 저장하고 정확 일치 건수와 결과 화면을 열 수 있는지를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색 조건이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
					@ExampleObject(name = "날짜 범위", value = INVALID_DATE),
					@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
					@ExampleObject(name = "지원하지 않는 자치구", value = MatchDiscoveryExamples.INVALID_SEARCH_AREA),
					@ExampleObject(name = "잘못된 시간 범위", value = MatchDiscoveryExamples.INVALID_TIME_RANGE),
					@ExampleObject(name = "잘못된 NTRP 범위", value = MatchDiscoveryExamples.INVALID_SEARCH_NTRP)
			}))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "활성 요청 상한에 도달했습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = LIMIT_REACHED)))
	public ResponseEntity<ApiResponse<MatchRequestCreatedResponse>> create(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody MatchSearchRequest conditions) {
		var response = requests.create(principal.userId(), conditions);
		var location = URI.create("/api/v1/match-requests/" + response.request().requestId() + "/results");
		return ResponseEntity.created(location).body(ApiResponse.of(SuccessStatus._CREATED, response));
	}

	@GetMapping
	@Operation(summary = "내 활성 매칭 요청 목록", description = "홈 카드에 표시할 요청을 최근 저장 순서로 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	public ResponseEntity<ApiResponse<List<MatchRequestResponse>>> list(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
		return ResponseEntity.ok(ApiResponse.onSuccess(requests.list(principal.userId())));
	}

	@GetMapping("/{requestId}/results")
	@Operation(summary = "저장한 매칭 요청의 결과 조회", description = "저장한 조건으로 현재의 정확 일치·비슷한 매치를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 또는 페이지가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
					@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
					@ExampleObject(name = "페이지 범위 초과", value = MatchDiscoveryExamples.PAGE_OUT_OF_RANGE)
			}))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 매칭 요청을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = NOT_FOUND)))
	public ResponseEntity<ApiResponse<MatchSearchResponse>> results(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@PathVariable @Positive Long requestId,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int exactPage,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int similarPage,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				requests.results(principal.userId(), requestId, exactPage, similarPage, size)));
	}

	@DeleteMapping("/{requestId}")
	@Operation(summary = "내 매칭 요청 삭제")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 완료")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 매칭 요청을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = NOT_FOUND)))
	public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@PathVariable @Positive Long requestId) {
		requests.delete(principal.userId(), requestId);
		return ResponseEntity.noContent().build();
	}
}
