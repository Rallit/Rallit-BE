package com.rallit.domain.match.controller;

import com.rallit.domain.match.controller.swagger.MatchRecommendationSwagger;
import com.rallit.domain.match.dto.response.MatchRecommendationResponse;
import com.rallit.domain.match.service.MatchRecommendationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/matches", produces = MediaType.APPLICATION_JSON_VALUE)
public class MatchRecommendationController implements MatchRecommendationSwagger {

	private final MatchRecommendationService recommendations;

	@Override
	@GetMapping("/recommendations")
	public ResponseEntity<ApiResponse<PageResponse<MatchRecommendationResponse>>> home(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(recommendations.home(principal.userId(), page, size)));
	}
}
