package com.rallit.domain.user.controller;

import com.rallit.domain.image.ImageLinkResponse;
import com.rallit.domain.image.ImageLinkService;
import com.rallit.domain.image.ImageUploadPurpose;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.service.ProfileImageService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "회원")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class ProfileImageController {
  private final ProfileImageService images;
  private final ImageLinkService links;

  @Operation(
      summary = "내 프로필 사진 등록 및 교체",
      description = "파일시스템 모드용입니다. JPG·JPEG·PNG, 최대 5MB입니다. 원본은 가로·세로 각각 4,096픽셀, 총 1,250만 픽셀까지 지원합니다.")
  @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ProfileImageResponse>> upload(
      @AuthenticationPrincipal UserDetails principal, @RequestPart("file") MultipartFile file) {
    links.requireFilesystem();
    return ResponseEntity.ok(ApiResponse.onSuccess(images.upload(principal.userId(), file)));
  }

  @Operation(summary = "회원 프로필 사진 파일 조회", description = "파일시스템 모드에서 프로필 사진을 반환합니다.")
  @GetMapping(value = "/{userId}/profile-image", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
  public ResponseEntity<Resource> get(@PathVariable Long userId) {
    links.requireFilesystem();
    var image = images.get(userId);
    return ResponseEntity.ok().contentType(image.mediaType()).body(image.contents());
  }

  @GetMapping("/{userId}/profile-image/url")
  @Operation(summary = "회원 프로필 사진 URL 조회", description = "로그인한 회원에게 이미지 URL과 만료 시각을 반환합니다.")
  public ResponseEntity<ApiResponse<ImageLinkResponse>> getProfileImageUrl(@PathVariable Long userId) {
    return ResponseEntity.ok(ApiResponse.onSuccess(
        links.link(ImageUploadPurpose.PROFILE, images.imageKey(userId), ProfileImageResponse.urlFor(userId))));
  }

  @Operation(
      summary = "내 프로필 사진 삭제",
      description = "삭제 후 내 정보의 `profileImageUrl`이 `null`이면 랠릿 기본 이미지를 표시합니다.",
      responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "204", description = "사진 삭제 완료 또는 삭제할 사진 없음", content = @Content))
  @DeleteMapping("/me/profile-image")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal) {
    images.delete(principal.userId());
    return ResponseEntity.noContent().build();
  }
}
