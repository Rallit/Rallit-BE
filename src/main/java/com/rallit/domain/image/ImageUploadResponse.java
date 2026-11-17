package com.rallit.domain.image;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ImageUploadResponse(
    Mode mode, UUID uploadId, String url, Map<String, String> fields, Instant expiresAt) {
  public enum Mode { FILESYSTEM, S3 }

  public static ImageUploadResponse filesystem() {
    return new ImageUploadResponse(Mode.FILESYSTEM, null, null, null, null);
  }
}
