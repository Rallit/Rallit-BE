package com.rallit.domain.match.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.rallit.domain.match.dto.response.MatchDetailResponse;
import com.rallit.domain.match.dto.response.MatchListItemResponse;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.service.MatchDiscoveryService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/matches", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "매치 조회")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
				description = "서비스 Bearer 토큰 누락, 만료 또는 인증 실패",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "인증 필요", value = MatchDiscoveryExamples.UNAUTHORIZED))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "온보딩을 완료해야 매치를 조회할 수 있습니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "온보딩 미완료", value = MatchDiscoveryExamples.ONBOARDING_REQUIRED))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
				description = "인증 세션 저장소를 일시적으로 사용할 수 없습니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "세션 저장소 장애", value = MatchDiscoveryExamples.SERVICE_UNAVAILABLE)))
})
public class MatchDiscoveryController {

	private final MatchDiscoveryService discovery;

	@GetMapping
	@Operation(summary = "모집 중 매치 목록",
			description = """
					승인된 코트 슬롯의 매치 중 모집 마감과 경기 시작 전인 매치를 조회합니다.

					- 경기 시작 시각이 빠른 순으로 정렬합니다. 시작 시각이 같으면 매치 ID가 작은 순으로 정렬합니다.
					- 응답 시각에는 서울 시간대 오프셋 `+09:00`이 포함됩니다.

					**페이지 응답**

					- `result.items` — 현재 페이지의 매치 목록
					- `result.totalElements` — 필터가 적용된 전체 매치 수
					""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
					description = "목록 조회 성공. 검색 결과가 없어도 첫 페이지는 빈 목록을 반환합니다.",
					useReturnTypeSchema = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = {
									@ExampleObject(name = "모집 매치 목록", value = MatchDiscoveryExamples.LIST),
									@ExampleObject(name = "검색 결과 없음", value = MatchDiscoveryExamples.EMPTY_LIST)
							})),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
					description = "요청 형식, 날짜와 시간 조건의 조합 또는 페이지가 유효하지 않습니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class), examples = {
									@ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST),
									@ExampleObject(name = "시간 필터에 날짜 필요", value = MatchDiscoveryExamples.DATE_REQUIRED),
									@ExampleObject(name = "잘못된 시간 범위", value = MatchDiscoveryExamples.INVALID_TIME_RANGE),
									@ExampleObject(name = "페이지 범위 초과", value = MatchDiscoveryExamples.PAGE_OUT_OF_RANGE)
							}))
	})
	public ResponseEntity<ApiResponse<PageResponse<MatchListItemResponse>>> list(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = "경기 시작일(서울 기준). 연도 0001~9999. 생략하면 현재 모집 중인 향후 경기를 조회합니다.",
					schema = @Schema(type = "string", format = "date", example = "2026-11-15"))
			@RequestParam(required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String date,
			@Parameter(description = "가장 이른 경기 시작 시각(HH:mm, 경계 포함). 입력 시 date가 필요합니다.",
					schema = @Schema(type = "string", example = "14:00"))
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeFrom,
			@Parameter(description = "가장 늦은 경기 종료 시각(HH:mm, 경계 포함). 입력 시 date가 필요하며, "
					+ "두 시간 조건을 함께 쓰면 시작 시간보다 늦어야 합니다.",
					schema = @Schema(type = "string", example = "18:00"))
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeTo,
			@Parameter(description = "경기 방식 중 하나 이상이 일치하면 포함합니다. "
					+ "`matchTypes=SINGLES&matchTypes=RALLY` 또는 `matchTypes=SINGLES,RALLY`로 전달할 수 있습니다.")
			@RequestParam(required = false) List<@NotNull MatchType> matchTypes,
			@Parameter(description = "true이면 본인 개설, 진행 중인 신청, 모집 성별과 확정 인원 기준 정원을 확인해 "
					+ "내가 신청할 수 있는 매치만 목록과 전체 건수에 포함합니다.")
			@RequestParam(defaultValue = "false") boolean applicableOnly,
			@Parameter(description = "0부터 시작하는 페이지. 첫 페이지 이후 결과 범위를 벗어나면 400입니다.")
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@Parameter(description = "페이지당 요청할 최대 항목 수")
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(
				discovery.list(principal.userId(), parseDate(date), startTimeFrom, startTimeTo, matchTypes, applicableOnly,
						page, size)));
	}

	@GetMapping("/{matchId}")
	@Operation(summary = "모집 매치 상세",
			description = """
					목록에서 받은 매치 ID로 코트와 호스트 정보를 조회합니다.

					응답 시각에는 서울 시간대 오프셋 `+09:00`이 포함됩니다.

					**신청 상태**

					- 모집 마감과 경기 시작 전이면 `recruitmentOpen` 값이 `true`입니다. 경기 시작 전에는 모집이 마감돼도 상세를 조회할 수 있습니다.
					- 조회 시점에 현재 회원이 신청할 수 있으면 `canApply` 값이 `true`입니다. 신청할 수 없는 이유는 `applicationBlockReason` 필드에 표시됩니다.

					참가 신청은 같은 매치 ID로 `POST /api/v1/matches/{matchId}/applications`를 호출합니다.
					""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
					description = "상세 조회 성공",
					useReturnTypeSchema = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "신청 가능", value = MatchDiscoveryExamples.DETAIL))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
					description = "매치 ID는 양의 정수여야 합니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class),
							examples = @ExampleObject(name = "잘못된 입력", value = MatchDiscoveryExamples.BAD_REQUEST))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
					description = "매치가 없거나 승인 슬롯의 모집 매치가 아니거나 이미 경기가 시작됐습니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class),
							examples = @ExampleObject(name = "조회 불가", value = MatchDiscoveryExamples.MATCH_NOT_FOUND)))
	})
	public ResponseEntity<ApiResponse<MatchDetailResponse>> detail(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = "목록 응답의 matchId", example = "42")
			@PathVariable @Positive Long matchId) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(discovery.detail(matchId, principal.userId())));
	}

	private void requireOnboarding(UserDetails principal) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_ONBOARDING_REQUIRED);
		}
	}

	private LocalDate parseDate(String value) {
		if (value == null) {
			return null;
		}
		try {
			LocalDate date = LocalDate.parse(value);
			if (date.getYear() > 0) {
				return date;
			}
		} catch (DateTimeParseException e) {
			throw new GeneralException(ErrorStatus._BAD_REQUEST, e);
		}
		throw new GeneralException(ErrorStatus._BAD_REQUEST);
	}
}
