package com.rallit.domain.match.controller;

import com.rallit.domain.match.controller.swagger.MatchDiscoverySwagger;
import com.rallit.domain.match.dto.request.MatchDate;
import com.rallit.domain.match.dto.response.MatchDetailResponse;
import com.rallit.domain.match.dto.response.MatchListItemResponse;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.service.MatchDiscoveryService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import java.time.LocalTime;
import java.util.List;
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
public class MatchDiscoveryController implements MatchDiscoverySwagger {

	private final MatchDiscoveryService discovery;

	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<MatchListItemResponse>>> list(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String date,
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeFrom,
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeTo,
			@RequestParam(required = false) List<MatchType> matchTypes,
			@RequestParam(defaultValue = "false") boolean applicableOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(
				discovery.list(principal.userId(), date == null ? null : MatchDate.parse(date), startTimeFrom,
						startTimeTo, matchTypes, applicableOnly, page, size)));
	}

	@Override
	@GetMapping("/{matchId}")
	public ResponseEntity<ApiResponse<MatchDetailResponse>> detail(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long matchId) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(discovery.detail(matchId, principal.userId())));
	}

	private void requireOnboarding(UserDetails principal) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_ONBOARDING_REQUIRED);
		}
	}
}
