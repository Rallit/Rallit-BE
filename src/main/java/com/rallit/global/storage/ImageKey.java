package com.rallit.global.storage;

import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;

public final class ImageKey {
  private static final Pattern FILE_KEY =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

  private ImageKey() {}

  static String requireValid(String key) throws IOException {
    if (!FILE_KEY.matcher(key).matches()) {
      throw new IOException("Invalid image key");
    }
    return key;
  }

  public static MediaType mediaType(String key) {
    return key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
  }
}
