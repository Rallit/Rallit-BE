package com.rallit.domain.user.controller;

import static com.rallit.domain.user.entity.OnboardingProfile.NICKNAME_PATTERN;

import com.rallit.domain.user.dto.NicknameAvailabilityResponse;
import com.rallit.domain.user.dto.OnboardingRequest;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;

  @Operation(
      summary = "내 회원 정보 조회",
      description = "로그인한 회원의 기본 정보와 완료된 온보딩 프로필을 조회합니다.")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(
      @AuthenticationPrincipal UserDetails principal) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(principal.userId())));
  }

  @Operation(summary = "온보딩 완료", description = "세 단계의 입력을 저장하고 가입을 완료합니다.")
  @PostMapping("/me/onboarding")
  public ResponseEntity<ApiResponse<UserResponse>> completeOnboarding(
      @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody OnboardingRequest request) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.completeOnboarding(principal.userId(), request)));
  }

  @Operation(summary = "닉네임 사용 가능 여부 조회")
  @GetMapping("/nickname-availability")
  public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNickname(
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam @Pattern(regexp = NICKNAME_PATTERN) String nickname) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.checkNickname(principal.userId(), nickname)));
  }
}
