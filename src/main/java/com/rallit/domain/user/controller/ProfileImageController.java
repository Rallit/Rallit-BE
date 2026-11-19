package com.rallit.domain.user.controller;

import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageLinkService;
import com.rallit.domain.user.controller.swagger.ProfileImageSwagger;
import com.rallit.domain.user.service.ProfileImageService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class ProfileImageController implements ProfileImageSwagger {
  private final ProfileImageService images;
  private final ImageLinkService links;

  @Override
  @GetMapping("/{userId}/profile-image/url")
  public ResponseEntity<ApiResponse<ImageLinkResponse>> getProfileImageUrl(@PathVariable Long userId) {
    return ResponseEntity.ok(ApiResponse.onSuccess(
        links.link(ImageUploadPurpose.PROFILE, images.imageKey(userId))));
  }

  @Override
  @DeleteMapping("/me/profile-image")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal) {
    images.delete(principal.userId());
    return ResponseEntity.noContent().build();
  }
}
