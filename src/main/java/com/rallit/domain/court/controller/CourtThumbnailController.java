package com.rallit.domain.court.controller;

import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.service.CourtThumbnailService;
import com.rallit.domain.image.ImageLinkResponse;
import com.rallit.domain.image.ImageLinkService;
import com.rallit.domain.image.ImageUploadPurpose;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "코트")
@SecurityRequirement(name = "bearerAuth")
public class CourtThumbnailController {
	private final CourtThumbnailService thumbnails;
	private final ImageLinkService links;

	@GetMapping("/{courtId}/thumbnail/url")
	@Operation(summary = "코트 대표 사진 URL 조회", description = "온보딩을 완료한 회원에게 이미지 URL과 만료 시각을 반환합니다.")
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
