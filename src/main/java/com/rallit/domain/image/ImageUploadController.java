package com.rallit.domain.image;

import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "이미지 업로드")
@SecurityRequirement(name = "bearerAuth")
public class ImageUploadController {
  private final ImageUploadService uploads;

  @PostMapping("/users/me/profile-image/uploads")
  @Operation(summary = "프로필 사진 업로드 발급",
      description = "S3에 사진을 올릴 URL과 서명 폼을 반환합니다.")
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateProfile(
      @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody ImageUploadRequest request) {
    Long userId = principal.userId();
    var result = uploads.initiate(userId, userId, ImageUploadPurpose.PROFILE, request);
    return ResponseEntity.ok(ApiResponse.onSuccess(result));
  }

  @PostMapping("/users/me/profile-image/uploads/{uploadId}/complete")
  @Operation(summary = "프로필 사진 업로드 완료", description = "S3에 올린 사진을 검증해 프로필에 반영합니다.")
  public ResponseEntity<ApiResponse<ProfileImageResponse>> completeProfile(
      @AuthenticationPrincipal UserDetails principal, @PathVariable UUID uploadId) {
    Long userId = principal.userId();
    uploads.complete(userId, userId, ImageUploadPurpose.PROFILE, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new ProfileImageResponse(ProfileImageResponse.urlFor(userId))));
  }

  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads")
  @Operation(summary = "코트 대표 사진 업로드 발급", description = "운영자 세션으로 S3 업로드 폼을 발급합니다.")
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId,
      @Valid @RequestBody ImageUploadRequest request) {
    return ResponseEntity.ok(ApiResponse.onSuccess(
        uploads.initiate(principal.userId(), courtId, ImageUploadPurpose.COURT, request)));
  }

  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads/{uploadId}/complete")
  @Operation(summary = "코트 대표 사진 업로드 완료", description = "S3에 올린 사진을 검증해 코트에 반영합니다.")
  public ResponseEntity<ApiResponse<CourtThumbnailResponse>> completeCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId, @PathVariable UUID uploadId) {
    uploads.complete(principal.userId(), courtId, ImageUploadPurpose.COURT, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new CourtThumbnailResponse(CourtThumbnailResponse.urlFor(courtId))));
  }

}
