package com.rallit.domain.match.controller;

import com.rallit.domain.match.controller.swagger.MatchApplicationSwagger;
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
import java.net.URI;
import java.time.LocalDate;
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

@RestController
@RequiredArgsConstructor
public class MatchApplicationController implements MatchApplicationSwagger {

	private final MatchApplicationService matchApplicationService;

	@Override
	@PostMapping("/api/v1/matches/{matchId}/applications")
	public ResponseEntity<ApiResponse<MatchApplicationCreateResponse>> apply(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long matchId,
			@RequestBody MatchApplicationCreateRequest request) {
		MatchApplicationCreateResponse response = matchApplicationService.apply(principal.userId(), matchId);
		return ResponseEntity.created(URI.create("/api/v1/applications/" + response.applicationId()))
				.body(ApiResponse.of(SuccessStatus._CREATED, response));
	}

	@Override
	@GetMapping("/api/v1/users/me/applications")
	public ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyScheduledApplications(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getMyScheduledApplications(principal.userId(), date, page, size)));
	}

	@Override
	@GetMapping("/api/v1/users/me/applications/closed")
	public ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyClosedApplications(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getMyClosedApplications(principal.userId(), page, size)));
	}

	@Override
	@GetMapping("/api/v1/applications/{applicationId}")
	public ResponseEntity<ApiResponse<MatchApplicationDetailResponse>> getApplication(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long applicationId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getApplication(principal.userId(), applicationId)));
	}

	@Override
	@PostMapping("/api/v1/applications/{applicationId}/cancel")
	public ResponseEntity<ApiResponse<MatchApplicationCancelResponse>> cancel(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long applicationId,
			@RequestBody(required = false) MatchApplicationCancelRequest request) {
		return ResponseEntity.ok(ApiResponse.onSuccess(matchApplicationService.cancel(principal.userId(),
				applicationId, request == null ? null : request.reason())));
	}
}
