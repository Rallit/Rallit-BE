package com.rallit.domain.auth.dto.response;

import com.rallit.domain.auth.dto.internal.SessionTokens;
import java.time.Instant;

public record TokenResponse(String accessToken, Instant accessExpiresAt, Instant sessionExpiresAt) {
  public static TokenResponse from(SessionTokens tokens) {
    return new TokenResponse(
        tokens.accessToken(), tokens.accessExpiresAt(), tokens.sessionExpiresAt());
  }

  @Override
  public String toString() {
    return "TokenResponse[REDACTED]";
  }
}
