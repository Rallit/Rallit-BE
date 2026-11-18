package com.rallit.domain.image.controller.swagger;

import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
public interface ImageUploadSwagger {

  @Operation(summary = "프로필 사진 업로드 발급",
      description = "S3에 사진을 올릴 URL과 서명 폼을 반환합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true,
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
  ResponseEntity<ApiResponse<ImageUploadResponse>> initiateProfile(
      UserDetails principal,
      @Valid ImageUploadRequest request);

  @Operation(summary = "프로필 사진 업로드 완료", description = "S3에 올린 사진을 검증해 프로필에 반영합니다.")
  @ImageUploadCompletionResponses
  ResponseEntity<ApiResponse<ProfileImageResponse>> completeProfile(
      UserDetails principal,
      UUID uploadId);

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
  ResponseEntity<ApiResponse<ImageUploadResponse>> initiateCourt(
      UserDetails principal,
      Long courtId,
      @Valid ImageUploadRequest request);

  @Operation(summary = "코트 대표 사진 업로드 완료", description = "S3에 올린 사진을 검증해 코트에 반영합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
      description = "운영자 권한이 필요합니다.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = ApiResponse.class)))
  @ImageUploadCompletionResponses
  ResponseEntity<ApiResponse<CourtThumbnailResponse>> completeCourt(
      UserDetails principal,
      Long courtId,
      UUID uploadId);
}
