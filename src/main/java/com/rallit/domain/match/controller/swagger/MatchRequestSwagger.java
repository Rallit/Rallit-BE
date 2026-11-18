package com.rallit.domain.match.controller.swagger;

import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRequestCreatedResponse;
import com.rallit.domain.match.dto.response.MatchRequestResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
import com.rallit.global.apiPayload.ApiResponse;
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
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "매칭 요청")
@SecurityRequirement(name = "bearerAuth")
@MatchReadErrorResponses
public interface MatchRequestSwagger {

	@Operation(summary = "매칭 요청 저장", description = "조건을 저장하고 정확 일치 건수와 결과 화면을 열 수 있는지를 반환합니다. "
			+ "오늘만 선택하면 시작 시각은 현재보다 늦어야 합니다. "
			+ "여러 날짜를 선택하면 같은 시간대를 저장하고, 미리보기에서 요청 시간대가 끝난 날짜를 건너뜁니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색 조건이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
					@ExampleObject(name = "날짜 범위", ref = MatchRequestErrorExamples.INVALID_DATE_REF),
					@ExampleObject(name = "오늘의 지난 시작 시각", ref = MatchRequestErrorExamples.PAST_START_TIME_REF),
					@ExampleObject(name = "동성만과 혼합복식", ref = MatchRequestErrorExamples.INVALID_PREFERENCES_REF),
					@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
					@ExampleObject(name = "지원하지 않는 자치구", value = MatchDiscoveryExamples.INVALID_SEARCH_AREA),
					@ExampleObject(name = "잘못된 시간 범위", value = MatchDiscoveryExamples.INVALID_TIME_RANGE),
					@ExampleObject(name = "잘못된 NTRP 범위", value = MatchDiscoveryExamples.INVALID_SEARCH_NTRP)
			}))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "활성 요청 상한에 도달했습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class),
					examples = @ExampleObject(name = "요청 상한", ref = MatchRequestErrorExamples.LIMIT_REACHED_REF)))
	ResponseEntity<ApiResponse<MatchRequestCreatedResponse>> create(
			@Parameter(hidden = true) UserDetails principal,
			@Valid MatchSearchRequest conditions);

	@Operation(summary = "내 활성 매칭 요청 목록", description = "홈 카드에 표시할 요청을 최근 저장 순서로 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	ResponseEntity<ApiResponse<List<MatchRequestResponse>>> list(
			@Parameter(hidden = true) UserDetails principal);

	@Operation(summary = "저장한 매칭 요청 조회", description = "본인이 저장한 활성 요청의 조건과 만료 시각을 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 ID는 양의 정수여야 합니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class),
					examples = @ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 매칭 요청을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class),
					examples = @ExampleObject(name = "요청 없음", ref = MatchRequestErrorExamples.NOT_FOUND_REF)))
	ResponseEntity<ApiResponse<MatchRequestResponse>> detail(
			@Parameter(hidden = true) UserDetails principal,
			@Positive Long requestId);

	@Operation(summary = "저장한 매칭 요청의 결과 조회",
			description = "저장한 조건으로 현재 신청 가능한 정확 일치·비슷한 매치를 조회합니다. "
					+ "여러 날짜를 선택한 요청은 시간대가 끝난 날짜를 건너뜁니다. 비슷한 매치는 지역만, 시간만, "
					+ "둘 다 순으로 완화해 첫 결과가 있는 단계만 반환합니다. 지역은 구 대표점에서 직선거리 5km 이내, "
					+ "시간은 각 선택일 안에서 앞뒤 1시간을 적용합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 형식 또는 페이지가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class), examples = {
					@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
					@ExampleObject(name = "페이지 범위 초과", value = MatchDiscoveryExamples.PAGE_OUT_OF_RANGE)
			}))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 매칭 요청을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class),
					examples = @ExampleObject(name = "요청 없음", ref = MatchRequestErrorExamples.NOT_FOUND_REF)))
	ResponseEntity<ApiResponse<MatchSearchResponse>> results(
			@Parameter(hidden = true) UserDetails principal,
			@Positive Long requestId,
			@Min(0) @Max(10000) int exactPage,
			@Min(0) @Max(10000) int similarPage,
			@Min(1) @Max(50) int size);

	@Operation(summary = "내 매칭 요청 삭제")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 완료")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 매칭 요청을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class),
					examples = @ExampleObject(name = "요청 없음", ref = MatchRequestErrorExamples.NOT_FOUND_REF)))
	ResponseEntity<Void> delete(@Parameter(hidden = true) UserDetails principal, @Positive Long requestId);
}
