package com.rallit.domain.image.controller;

import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageUploadService;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
        description = "요청 형식, 파일명 또는 실제 이미지가 유효하지 않습니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
        description = "서비스 또는 운영자 인증이 필요합니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
        description = "이미지 크기가 5MiB를 초과합니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
        description = "이미지 저장소 장애 또는 정규화 처리 한도 초과입니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class)))
})
public class ImageUploadController {
  private final ImageUploadService uploads;

  @PostMapping("/users/me/profile-image/uploads")
  @Operation(summary = "프로필 사진 업로드 발급",
      description = "S3에 사진을 올릴 URL과 서명 폼을 반환합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true,
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateProfile(
      @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody ImageUploadRequest request) {
    Long userId = principal.userId();
    var result = uploads.initiate(userId, userId, ImageUploadPurpose.PROFILE, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, result));
  }

  @PostMapping("/users/me/profile-image/uploads/{uploadId}/complete")
  @Operation(summary = "프로필 사진 업로드 완료", description = "S3에 올린 사진을 검증해 프로필에 반영합니다.")
  @ImageUploadCompletionResponses
  public ResponseEntity<ApiResponse<ProfileImageResponse>> completeProfile(
      @AuthenticationPrincipal UserDetails principal, @PathVariable UUID uploadId) {
    Long userId = principal.userId();
    uploads.complete(userId, userId, ImageUploadPurpose.PROFILE, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new ProfileImageResponse(ProfileImageResponse.urlFor(userId))));
  }

  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads")
  @Operation(summary = "코트 대표 사진 업로드 발급", description = "운영자 세션으로 S3 업로드 폼을 발급합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true,
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
      description = "운영자 권한이 필요합니다.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = ApiResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "코트를 찾을 수 없습니다.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = ApiResponse.class)))
  public ResponseEntity<ApiResponse<ImageUploadResponse>> initiateCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId,
      @Valid @RequestBody ImageUploadRequest request) {
    var result = uploads.initiate(principal.userId(), courtId, ImageUploadPurpose.COURT, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessStatus._CREATED, result));
  }

  @PostMapping("/admin/courts/{courtId}/thumbnail/uploads/{uploadId}/complete")
  @Operation(summary = "코트 대표 사진 업로드 완료", description = "S3에 올린 사진을 검증해 코트에 반영합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
      description = "운영자 권한이 필요합니다.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = ApiResponse.class)))
  @ImageUploadCompletionResponses
  public ResponseEntity<ApiResponse<CourtThumbnailResponse>> completeCourt(
      @AuthenticationPrincipal UserDetails principal, @PathVariable Long courtId, @PathVariable UUID uploadId) {
    uploads.complete(principal.userId(), courtId, ImageUploadPurpose.COURT, uploadId);
    return ResponseEntity.ok(ApiResponse.onSuccess(new CourtThumbnailResponse(CourtThumbnailResponse.urlFor(courtId))));
  }

}
