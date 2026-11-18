package com.rallit.domain.user.controller;

import com.rallit.domain.user.controller.swagger.UserSwagger;
import com.rallit.domain.user.dto.NicknameAvailabilityResponse;
import com.rallit.domain.user.dto.OnboardingRequest;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserSwagger {
  private final UserService users;

  @Override
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(
      @AuthenticationPrincipal UserDetails principal) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(principal.userId())));
  }

  @Override
  @PostMapping("/me/onboarding")
  public ResponseEntity<ApiResponse<UserResponse>> completeOnboarding(
      @AuthenticationPrincipal UserDetails principal, @RequestBody OnboardingRequest request) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.completeOnboarding(principal.userId(), request)));
  }

  @Override
  @GetMapping("/nickname-availability")
  public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNickname(
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam String nickname) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.checkNickname(principal.userId(), nickname)));
  }
}
