package com.rallit.global.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rallit.s3.presigned")
public record S3PresignedProperties(
    @Positive long uploadExpirySeconds,
    @Positive @Max(604800) long downloadExpirySeconds) {
  public Duration uploadLifetime() {
    return Duration.ofSeconds(uploadExpirySeconds);
  }

  public Duration downloadLifetime() {
    return Duration.ofSeconds(downloadExpirySeconds);
  }
}
