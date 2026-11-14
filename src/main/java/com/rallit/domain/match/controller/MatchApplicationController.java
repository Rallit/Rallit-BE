package com.rallit.domain.match.controller;

import java.net.URI;
import java.time.LocalDate;

import com.rallit.domain.match.dto.request.MatchApplicationCancelRequest;
import com.rallit.domain.match.dto.request.MatchApplicationCreateRequest;
import com.rallit.domain.match.dto.response.MatchApplicationCancelResponse;
import com.rallit.domain.match.dto.response.MatchApplicationCreateResponse;
import com.rallit.domain.match.dto.response.MatchApplicationDetailResponse;
import com.rallit.domain.match.dto.response.MyMatchApplicationListItemResponse;
import com.rallit.domain.match.service.MatchApplicationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "매치 참가 신청")
@RestController
@RequiredArgsConstructor
public class MatchApplicationController {

	private static final int MAX_PAGE = 10_000;

	private final MatchApplicationService matchApplicationService;

	@Operation(
			summary = "매치 참가 신청",
			description = "모집 중인 매치에 참가를 신청합니다. 신청은 호스트 수락 전 PENDING 상태이며 좌석을 점유하지 않습니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@PostMapping("/api/v1/matches/{matchId}/applications")
	public ResponseEntity<ApiResponse<MatchApplicationCreateResponse>> apply(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long matchId,
			@Valid @RequestBody MatchApplicationCreateRequest request) {
		MatchApplicationCreateResponse response = matchApplicationService.apply(principal.userId(), matchId);
		return ResponseEntity.created(URI.create("/api/v1/applications/" + response.applicationId()))
				.body(ApiResponse.of(SuccessStatus._CREATED, response));
	}

	@Operation(
			summary = "매치 현황 조회",
			description = "진행 중인 참가 신청(PENDING, PAYMENT_PENDING, CONFIRMED)을 경기 시작 일시 오름차순으로 조회합니다. "
					+ "date가 없으면 오늘 이후 경기를, date가 있으면 해당 날짜의 경기만 조회합니다. "
					+ "startsAt과 endsAt에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/api/v1/users/me/applications")
	public ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyScheduledApplications(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getMyScheduledApplications(principal.userId(), date, page, size)));
	}

	@Operation(
			summary = "거절·취소·기한 만료 신청 조회",
			description = "종료된 참가 신청(REJECTED, CANCELED, EXPIRED)을 최근 처리순으로 조회합니다. "
					+ "startsAt과 endsAt에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/api/v1/users/me/applications/closed")
	public ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyClosedApplications(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getMyClosedApplications(principal.userId(), page, size)));
	}

	@Operation(
			summary = "참가 신청 상세 조회",
			description = "본인의 참가 신청 상세를 조회합니다. 상태에 따라 값이 있는 필드만 응답합니다. "
					+ "match의 startsAt, endsAt, recruitmentDeadline에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/api/v1/applications/{applicationId}")
	public ResponseEntity<ApiResponse<MatchApplicationDetailResponse>> getApplication(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long applicationId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getApplication(principal.userId(), applicationId)));
	}

	@Operation(
			summary = "결제 전 참가 신청 취소",
			description = "PENDING 상태의 본인 신청을 취소합니다. 이미 취소된 신청은 기존 취소 정보를 그대로 반환합니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@PostMapping("/api/v1/applications/{applicationId}/cancel")
	public ResponseEntity<ApiResponse<MatchApplicationCancelResponse>> cancel(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long applicationId,
			@RequestBody(required = false) MatchApplicationCancelRequest request) {
		return ResponseEntity.ok(ApiResponse.onSuccess(matchApplicationService.cancel(principal.userId(),
				applicationId, request == null ? null : request.reason())));
	}
}
