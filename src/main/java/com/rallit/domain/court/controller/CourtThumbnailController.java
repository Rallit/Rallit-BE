package com.rallit.domain.court.controller;

import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.service.CourtThumbnailService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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

	@GetMapping(value = "/{courtId}/thumbnail", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
	@Operation(summary = "코트 대표 사진 조회", description = "온보딩을 완료한 회원에게 코트 대표 사진을 반환합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코트 대표 사진",
					content = {
							@Content(mediaType = MediaType.IMAGE_JPEG_VALUE,
									schema = @Schema(type = "string", format = "binary")),
							@Content(mediaType = MediaType.IMAGE_PNG_VALUE,
									schema = @Schema(type = "string", format = "binary"))
					}),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "온보딩 미완료",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코트 또는 사진 없음",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "저장소 장애",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ApiResponse.class)))
	})
	public ResponseEntity<Resource> getCourtThumbnail(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(CourtErrorStatus.COURT_ONBOARDING_REQUIRED);
		}
		var thumbnail = thumbnails.get(courtId);
		return ResponseEntity.ok().contentType(thumbnail.mediaType()).body(thumbnail.contents());
	}
}
