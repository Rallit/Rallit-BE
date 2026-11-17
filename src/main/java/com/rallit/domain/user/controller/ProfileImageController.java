package com.rallit.domain.user.controller;

import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.domain.image.service.ImageLinkService;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.user.service.ProfileImageService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class ProfileImageController {
  private final ProfileImageService images;
  private final ImageLinkService links;

  @GetMapping("/{userId}/profile-image/url")
  @Operation(summary = "회원 프로필 사진 URL 조회", description = "로그인한 회원에게 이미지 URL과 만료 시각을 반환합니다.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
          description = "회원 인증이 필요합니다.",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
          description = "프로필 사진을 찾을 수 없습니다.",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
          description = "이미지 URL을 일시적으로 발급할 수 없습니다.",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiResponse.class)))
  })
  public ResponseEntity<ApiResponse<ImageLinkResponse>> getProfileImageUrl(@PathVariable Long userId) {
    return ResponseEntity.ok(ApiResponse.onSuccess(
        links.link(ImageUploadPurpose.PROFILE, images.imageKey(userId))));
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
