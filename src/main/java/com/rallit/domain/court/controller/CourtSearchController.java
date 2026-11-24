package com.rallit.domain.court.controller;

import java.math.BigDecimal;

import com.rallit.domain.court.controller.swagger.CourtSearchSwagger;
import com.rallit.domain.court.dto.CourtPlaceResponse;
import com.rallit.domain.court.dto.CourtRegisterRequest;
import com.rallit.domain.court.dto.CourtResponse;
import com.rallit.domain.court.enums.CourtSearchSort;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.service.CourtSearchService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/courts", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourtSearchController implements CourtSearchSwagger {

	private final CourtSearchService courts;

	@Override
	@GetMapping("/search")
	public ResponseEntity<ApiResponse<PageResponse<CourtPlaceResponse>>> search(
			@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) BigDecimal latitude,
			@RequestParam(required = false) BigDecimal longitude,
			@RequestParam(required = false) Integer radius,
			@RequestParam(required = false) CourtSearchSort sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "15") int size) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(
				courts.search(principal.userId(), keyword, latitude, longitude, radius, sort, page, size)));
	}

	@Override
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<CourtResponse>> register(
			@AuthenticationPrincipal UserDetails principal,
			@RequestBody CourtRegisterRequest request) {
		requireOnboarding(principal);
		var registration = courts.register(principal.userId(), request);
		if (!registration.created()) {
			return ResponseEntity.ok(ApiResponse.onSuccess(registration.court()));
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, registration.court()));
	}

	private void requireOnboarding(UserDetails principal) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_ONBOARDING_REQUIRED);
		}
	}
}
