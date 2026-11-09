package com.rallit.domain.user.controller;

import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.RallitPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(
      @AuthenticationPrincipal RallitPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(principal.userId())));
  }
}
