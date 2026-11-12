package com.rallit.domain.auth.controller;

import com.rallit.domain.auth.dto.response.TokenResponse;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.AuthRequestMatchers;
import com.rallit.global.security.authentication.UserDetails;
import com.rallit.global.security.cookie.AuthCookieManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@RestController
@RequiredArgsConstructor
public class AuthController {
  private final TokenSessionService sessions;
  private final AuthCookieManager cookies;
  private final UserService users;

  @Operation(
      summary = "액세스 토큰 재발급",
      description = "Refresh 쿠키를 갱신하고 새 액세스 토큰을 응답 본문으로 반환합니다.")
  @PostMapping({"/api/v1/auth/refresh", "/api/v1/admin/auth/refresh"})
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    var target = target(request);
    var tokens = sessions.refresh(cookies.read(request, cookies.name(target, "refresh")), target);
    cookies.writeRefresh(response, tokens, target);
    return ResponseEntity.ok(ApiResponse.onSuccess(TokenResponse.from(tokens)));
  }

  @Operation(
      summary = "로그아웃",
      description = "현재 세션을 종료하고 Refresh 쿠키를 삭제합니다.",
      responses =
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "204",
              description = "로그아웃 완료"))
  @PostMapping({"/api/v1/auth/logout", "/api/v1/admin/auth/logout"})
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    var t = target(request);
    try {
      sessions.logout(cookies.read(request, cookies.name(t, "refresh")), t);
    } finally {
      cookies.clear(response, t);
    }
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "내 운영자 정보 조회",
      description = "운영자 액세스 토큰으로 현재 로그인한 운영자의 정보를 조회합니다.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @GetMapping("/api/v1/admin/me")
  public ResponseEntity<ApiResponse<UserResponse>> adminMe(@AuthenticationPrincipal UserDetails p) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(p.userId())));
  }

  private LoginTarget target(HttpServletRequest r) {
    return AuthRequestMatchers.isAdmin(r) ? LoginTarget.ADMIN : LoginTarget.SERVICE;
  }
}
