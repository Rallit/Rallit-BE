package com.rallit.global.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rallit.kakao.local")
public record KakaoLocalProperties(
    String restApiKey,
    @NotNull URI baseUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @Positive int requestsPerMinute) {
  @Override
  public String toString() {
    return "KakaoLocalProperties[redacted]";
  }
}
