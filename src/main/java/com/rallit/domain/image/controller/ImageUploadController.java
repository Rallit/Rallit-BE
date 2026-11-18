package com.rallit.domain.image.controller;

import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.image.controller.swagger.ImageUploadSwagger;
import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageUploadService;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.security.authentication.UserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ImageUploadController implements ImageUploadSwagger {
  private final ImageUploadService uploads;

  @Override
  @PostMapping("/users/me/profile-image/uploads")
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateProfile(
      @AuthenticationPrincipal UserDetails principal, @RequestBody ImageUploadRequest request) {
    Long userId = principal.userId();
    var result = uploads.initiate(userId, userId, ImageUploadPurpose.PROFILE, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, result));
  }

  @Override
  @PostMapping("/users/me/profile-image/uploads/{uploadId}/complete")
  public ResponseEntity<ApiResponse<ProfileImageResponse>> completeProfile(
      @AuthenticationPrincipal UserDetails principal, @PathVariable UUID uploadId) {
    Long userId = principal.userId();
    uploads.complete(userId, userId, ImageUploadPurpose.PROFILE, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new ProfileImageResponse(ProfileImageResponse.urlFor(userId))));
  }

  @Override
  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads")
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId,
      @RequestBody ImageUploadRequest request) {
    var result = uploads.initiate(principal.userId(), courtId, ImageUploadPurpose.COURT, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, result));
  }

  @Override
  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads/{uploadId}/complete")
  public ResponseEntity<ApiResponse<CourtThumbnailResponse>> completeCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId, @PathVariable UUID uploadId) {
    uploads.complete(principal.userId(), courtId, ImageUploadPurpose.COURT, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new CourtThumbnailResponse(CourtThumbnailResponse.urlFor(courtId))));
  }

}
