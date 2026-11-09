package com.rallit.domain.auth.controller;

import com.rallit.domain.auth.dto.TokenResponse;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.RallitPrincipal;
import com.rallit.global.security.web.AuthCookieManager;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {
  private final TokenSessionService sessions;
  private final AuthCookieManager cookies;
  private final UserService users;

  @PostMapping({"/api/v1/auth/refresh", "/api/v1/admin/auth/refresh"})
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    var target = target(request);
    var tokens = sessions.refresh(cookies.read(request, cookies.name(target, "refresh")), target);
    cookies.writeRefresh(response, tokens, target);
    return ResponseEntity.ok(ApiResponse.onSuccess(TokenResponse.from(tokens)));
  }

  @PostMapping({"/api/v1/auth/logout", "/api/v1/admin/auth/logout"})
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    var t = target(request);
    sessions.logout(cookies.read(request, cookies.name(t, "refresh")), t);
    cookies.clear(response, t);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/admin/me")
  public ResponseEntity<ApiResponse<UserResponse>> adminMe(
      @AuthenticationPrincipal RallitPrincipal p) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(p.userId())));
  }

  private LoginTarget target(HttpServletRequest r) {
    return com.rallit.global.security.web.AuthRequestMatchers.isAdmin(r)
        ? LoginTarget.ADMIN
        : LoginTarget.SERVICE;
  }
}
