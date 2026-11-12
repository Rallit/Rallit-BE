package com.rallit.domain.user.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface ProfileImageStorage {
  void write(String key, InputStream contents) throws IOException;

  Resource read(String key) throws IOException;

  void delete(String key) throws IOException;
}
