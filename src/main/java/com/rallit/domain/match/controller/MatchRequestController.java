package com.rallit.domain.match.controller;

import com.rallit.domain.match.controller.swagger.MatchRequestSwagger;
import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRequestCreatedResponse;
import com.rallit.domain.match.dto.response.MatchRequestResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
import com.rallit.domain.match.service.MatchRequestService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.security.authentication.UserDetails;
import java.net.URI;
import java.util.List;
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
public class MatchRequestController implements MatchRequestSwagger {

	private final MatchRequestService requests;

	@Override
	@PostMapping
	public ResponseEntity<ApiResponse<MatchRequestCreatedResponse>> create(
			@AuthenticationPrincipal UserDetails principal,
			@RequestBody MatchSearchRequest conditions) {
		var response = requests.create(principal.userId(), conditions);
		var location = URI.create("/api/v1/match-requests/" + response.request().requestId());
		return ResponseEntity.created(location).body(ApiResponse.of(SuccessStatus._CREATED, response));
	}

	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<List<MatchRequestResponse>>> list(
			@AuthenticationPrincipal UserDetails principal) {
		return ResponseEntity.ok(ApiResponse.onSuccess(requests.list(principal.userId())));
	}

	@Override
	@GetMapping("/{requestId}")
	public ResponseEntity<ApiResponse<MatchRequestResponse>> detail(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long requestId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(requests.detail(principal.userId(), requestId)));
	}

	@Override
	@GetMapping("/{requestId}/results")
	public ResponseEntity<ApiResponse<MatchSearchResponse>> results(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long requestId,
			@RequestParam(defaultValue = "0") int exactPage,
			@RequestParam(defaultValue = "0") int similarPage,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(
				requests.results(principal.userId(), requestId, exactPage, similarPage, size)));
	}

	@Override
	@DeleteMapping("/{requestId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long requestId) {
		requests.delete(principal.userId(), requestId);
		return ResponseEntity.noContent().build();
	}
}
