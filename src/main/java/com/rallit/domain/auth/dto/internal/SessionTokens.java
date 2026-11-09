package com.rallit.domain.auth.dto.internal;

import java.time.Instant;

public record SessionTokens(
    String accessToken, String refreshToken, Instant accessExpiresAt, Instant sessionExpiresAt) {
  @Override
  public String toString() {
    return "SessionTokens[REDACTED]";
  }
}
