package com.rallit.domain.match.controller;

import java.net.URI;

import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageUploadService;
import com.rallit.domain.match.controller.swagger.MatchCreationSwagger;
import com.rallit.domain.match.dto.request.MatchCreateRequest;
import com.rallit.domain.match.dto.response.MatchCreateResponse;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.service.MatchCreationService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MatchCreationController implements MatchCreationSwagger {

	private final MatchCreationService creation;
	private final ImageUploadService uploads;

	@Override
	@PostMapping(value = "/api/v1/host/reservation-evidence/uploads", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateEvidenceUpload(
			@AuthenticationPrincipal UserDetails principal,
			@RequestBody ImageUploadRequest request) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_CREATE_ONBOARDING_REQUIRED);
		}
		Long userId = principal.userId();
		var result = uploads.initiate(userId, userId, ImageUploadPurpose.RESERVATION_EVIDENCE, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, result));
	}

	@Override
	@PostMapping(value = "/api/v1/matches", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<MatchCreateResponse>> create(
			@AuthenticationPrincipal UserDetails principal,
			@RequestBody MatchCreateRequest request) {
		var response = creation.create(principal.userId(), request);
		return ResponseEntity.created(URI.create("/api/v1/matches/" + response.matchId()))
				.body(ApiResponse.of(SuccessStatus._CREATED, response));
	}
}
