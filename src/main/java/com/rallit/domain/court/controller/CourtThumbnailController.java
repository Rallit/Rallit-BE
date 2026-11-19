package com.rallit.domain.court.controller;

import com.rallit.domain.court.controller.swagger.CourtThumbnailSwagger;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.service.CourtThumbnailService;
import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageLinkService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courts")
public class CourtThumbnailController implements CourtThumbnailSwagger {
	private final CourtThumbnailService thumbnails;
	private final ImageLinkService links;

	@Override
	@GetMapping("/{courtId}/thumbnail/url")
	public ResponseEntity<ApiResponse<ImageLinkResponse>> getCourtThumbnailUrl(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(links.link(ImageUploadPurpose.COURT,
				thumbnails.imageKey(courtId))));
	}

	private void requireOnboarding(UserDetails principal) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(CourtErrorStatus.COURT_ONBOARDING_REQUIRED);
		}
	}
}
