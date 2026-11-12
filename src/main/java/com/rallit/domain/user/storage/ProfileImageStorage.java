package com.rallit.domain.user.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.Resource;

public interface ProfileImageStorage {
  void write(String key, InputStream contents) throws IOException;

  /** Returns empty when the key does not exist. Other storage failures are reported as IO errors. */
  Optional<Resource> read(String key) throws IOException;

  void delete(String key) throws IOException;
}
