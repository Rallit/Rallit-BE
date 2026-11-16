package com.rallit.domain.match.controller;

import com.rallit.domain.match.dto.request.MatchApplicationRejectRequest;
import com.rallit.domain.match.dto.response.HostMatchApplicationListResponse;
import com.rallit.domain.match.dto.response.MatchApplicationAcceptResponse;
import com.rallit.domain.match.dto.response.MatchApplicationRejectResponse;
import com.rallit.domain.match.service.MatchApplicationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/host/matches/{matchId}/applications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "호스트 - 참가 신청자 관리",
		description = "'내가 연 매치' 화면(04-D)에서 호스트가 자신이 개설한 매치의 새 신청을 수락·거절하고 모집 현황을 확인하는 API입니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
				description = "서비스 Bearer 토큰 누락, 만료 또는 인증 실패",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "인증 필요", value = HostMatchApplicationExamples.UNAUTHORIZED))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "로그인한 회원이 이 매치의 호스트가 아닙니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "호스트 아님", value = HostMatchApplicationExamples.NOT_MATCH_HOST)))
})
public class HostMatchApplicationController {

	private final MatchApplicationService matchApplicationService;

	@GetMapping
	@Operation(summary = "참가 신청자 관리 화면 조회",
			description = """
					'내가 연 매치' 화면 데이터를 조회합니다.

					- 모집 현황 `2/4` — `recruitment.currentParticipantCount` / `recruitment.capacity`
					- 새 신청 — `pendingApplications`
					- 참가자 — `participants` (첫 항목이 `host: true`인 호스트 본인)
					- 참가자 배지 — `status`가 `PAYMENT_PENDING`이면 입금 대기 중, `CONFIRMED`이면 입금 완료
					""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
					useReturnTypeSchema = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "신청 관리 화면", value = HostMatchApplicationExamples.LIST))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매치가 없습니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class),
							examples = @ExampleObject(name = "매치 없음", value = HostMatchApplicationExamples.MATCH_NOT_FOUND)))
	})
	public ResponseEntity<ApiResponse<HostMatchApplicationListResponse>> getApplications(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = "호스트가 개설한 매치 ID", example = "42") @PathVariable Long matchId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getHostApplications(principal.userId(), matchId)));
	}

	@PostMapping("/{applicationId}/accept")
	@Operation(summary = "새 신청 수락",
			description = """
					새 신청을 수락합니다. 수락된 신청자는 참가자 목록에 입금 대기 중으로 표시됩니다.
					모집 마감이나 최종 마감(경기 시작 1시간 전)이 지나면 수락할 수 없습니다.

					성공하면 조회 API를 다시 호출해 화면을 갱신하세요.
					""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 성공",
					useReturnTypeSchema = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "수락", value = HostMatchApplicationExamples.ACCEPT))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
					description = "매치가 없거나 이 매치의 신청이 아닙니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class), examples = {
									@ExampleObject(name = "매치 없음", value = HostMatchApplicationExamples.MATCH_NOT_FOUND),
									@ExampleObject(name = "신청 없음", value = HostMatchApplicationExamples.APPLICATION_NOT_FOUND)
							})),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
					description = "해당 성별 자리가 다 찼거나, 이미 처리된 신청이거나, 모집이 끝난 매치입니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class), examples = {
									@ExampleObject(name = "성별 잔여석 없음", value = HostMatchApplicationExamples.SEAT_UNAVAILABLE),
									@ExampleObject(name = "이미 처리된 신청",
											value = HostMatchApplicationExamples.APPLICATION_NOT_PENDING),
									@ExampleObject(name = "모집 중 아님", value = HostMatchApplicationExamples.MATCH_NOT_RECRUITING),
									@ExampleObject(name = "모집 마감", value = HostMatchApplicationExamples.MATCH_RECRUITMENT_CLOSED)
							}))
	})
	public ResponseEntity<ApiResponse<MatchApplicationAcceptResponse>> accept(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = "호스트가 개설한 매치 ID", example = "42") @PathVariable Long matchId,
			@Parameter(description = "pendingApplications의 applicationId", example = "103")
			@PathVariable Long applicationId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.accept(principal.userId(), matchId, applicationId)));
	}

	@PostMapping("/{applicationId}/reject")
	@Operation(summary = "새 신청 거절",
			description = """
					새 신청을 거절합니다. 거절 사유는 선택이며 본문 없이 호출해도 됩니다.

					성공하면 조회 API를 다시 호출해 화면을 갱신하세요.
					""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거절 성공",
					useReturnTypeSchema = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "거절", value = HostMatchApplicationExamples.REJECT))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
					description = "거절 사유가 정해진 값이 아닙니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class),
							examples = @ExampleObject(name = "잘못된 사유", value = HostMatchApplicationExamples.BAD_REQUEST))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
					description = "매치가 없거나 이 매치의 신청이 아닙니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class), examples = {
									@ExampleObject(name = "매치 없음", value = HostMatchApplicationExamples.MATCH_NOT_FOUND),
									@ExampleObject(name = "신청 없음", value = HostMatchApplicationExamples.APPLICATION_NOT_FOUND)
							})),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 처리된 신청입니다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class),
							examples = @ExampleObject(name = "이미 처리된 신청",
									value = HostMatchApplicationExamples.APPLICATION_NOT_PENDING)))
	})
	public ResponseEntity<ApiResponse<MatchApplicationRejectResponse>> reject(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
			@Parameter(description = "호스트가 개설한 매치 ID", example = "42") @PathVariable Long matchId,
			@Parameter(description = "pendingApplications의 applicationId", example = "103")
			@PathVariable Long applicationId,
			@RequestBody(required = false) MatchApplicationRejectRequest request) {
		return ResponseEntity.ok(ApiResponse.onSuccess(matchApplicationService.reject(principal.userId(), matchId,
				applicationId, request == null ? null : request.reason())));
	}
}
