package com.rallit.domain.user.storage;

import com.rallit.global.storage.FileSystemImageStorage;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "filesystem", matchIfMissing = true)
public class FileSystemProfileImageStorage extends FileSystemImageStorage implements ProfileImageStorage {
  public FileSystemProfileImageStorage(@Value("${rallit.profile-image.storage-path}") String directory) {
    super(directory);
  }

  @Override
  public void write(String key, InputStream contents, long contentLength, MediaType mediaType)
      throws IOException {
    write(key, contents);
  }
}
