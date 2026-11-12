package com.rallit.global.security.authentication;

import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {
  private final ObjectMapper mapper;

  public void denied(
      jakarta.servlet.http.HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException exception)
      throws IOException {
    write(response, 403);
  }

  public void write(HttpServletResponse response, int status) throws IOException {
    write(
        response,
        status,
        "AUTH" + status,
        status == 401 ? "인증이 필요합니다." : status == 400 ? "잘못된 요청입니다." : "접근 권한이 없습니다.");
  }

  public void write(HttpServletResponse response, ErrorStatus error) throws IOException {
    write(response, error.getHttpStatus().value(), error.getCode(), error.getMessage());
  }

  private void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    mapper.writeValue(response.getWriter(), ApiResponse.onFailure(code, message, null));
  }
}
