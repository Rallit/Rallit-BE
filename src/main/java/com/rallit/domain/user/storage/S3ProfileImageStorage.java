package com.rallit.domain.user.storage;

import com.rallit.global.storage.S3ImageObjects;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "s3")
public class S3ProfileImageStorage implements ProfileImageStorage {
  private static final String PREFIX = "profile-images/";
  private final S3ImageObjects objects;

  @Override
  public void write(String key, InputStream contents, long contentLength, MediaType mediaType)
      throws IOException {
    objects.write(PREFIX, key, contents, contentLength, mediaType);
  }

  @Override
  public Optional<Resource> read(String key) throws IOException {
    return objects.read(PREFIX, key);
  }

  @Override
  public void delete(String key) throws IOException {
    objects.delete(PREFIX, key);
  }
}
