package com.rallit.domain.auth.controller.swagger;

import com.rallit.domain.auth.dto.response.TokenResponse;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증")
public interface AuthSwagger {

  @Operation(
      summary = "액세스 토큰 재발급",
      description = "Refresh 쿠키를 갱신하고 새 액세스 토큰을 응답 본문으로 반환합니다.")
  ResponseEntity<ApiResponse<TokenResponse>> refresh(
      HttpServletRequest request,
      HttpServletResponse response);

  @Operation(
      summary = "로그아웃",
      description = "현재 세션을 종료하고 Refresh 쿠키를 삭제합니다.",
      responses = {
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "204",
              description = "로그아웃 완료"),
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "503",
              description = "세션 종료를 확인하지 못했습니다. Refresh 쿠키를 유지해 다시 시도할 수 있습니다.")
      })
  ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response);

  @Operation(
      summary = "내 운영자 정보 조회",
      description = "운영자 액세스 토큰으로 현재 로그인한 운영자의 정보를 조회합니다.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<ApiResponse<UserResponse>> adminMe(UserDetails p);
}
