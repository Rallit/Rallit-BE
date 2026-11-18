package com.rallit.domain.match.controller;

import com.rallit.domain.match.controller.swagger.HostMatchApplicationSwagger;
import com.rallit.domain.match.dto.request.MatchApplicationRejectRequest;
import com.rallit.domain.match.dto.response.HostMatchApplicationListResponse;
import com.rallit.domain.match.dto.response.MatchApplicationAcceptResponse;
import com.rallit.domain.match.dto.response.MatchApplicationRejectResponse;
import com.rallit.domain.match.service.MatchApplicationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
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
public class HostMatchApplicationController implements HostMatchApplicationSwagger {

	private final MatchApplicationService matchApplicationService;

	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<HostMatchApplicationListResponse>> getApplications(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long matchId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.getHostApplications(principal.userId(), matchId)));
	}

	@Override
	@PostMapping("/{applicationId}/accept")
	public ResponseEntity<ApiResponse<MatchApplicationAcceptResponse>> accept(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long matchId,
			@PathVariable Long applicationId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				matchApplicationService.accept(principal.userId(), matchId, applicationId)));
	}

	@Override
	@PostMapping("/{applicationId}/reject")
	public ResponseEntity<ApiResponse<MatchApplicationRejectResponse>> reject(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long matchId,
			@PathVariable Long applicationId,
			@RequestBody(required = false) MatchApplicationRejectRequest request) {
		return ResponseEntity.ok(ApiResponse.onSuccess(matchApplicationService.reject(principal.userId(), matchId,
				applicationId, request == null ? null : request.reason())));
	}
}
