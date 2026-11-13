package com.rallit.domain.user.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class FileSystemProfileImageStorage implements ProfileImageStorage {
  private static final Pattern FILE_KEY =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

  private final Path directory;

  public FileSystemProfileImageStorage(@Value("${rallit.profile-image.storage-path}") String directory) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  @Override
  public void write(String key, InputStream contents) throws IOException {
    Path destination = pathFor(key);
    Files.createDirectories(directory);
    var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try (output) {
      contents.transferTo(output);
    } catch (IOException failure) {
      try {
        Files.deleteIfExists(destination);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  @Override
  public Optional<Resource> read(String key) throws IOException {
    try {
      return Optional.of(new InputStreamResource(Files.newInputStream(pathFor(key))));
    } catch (NoSuchFileException missing) {
      return Optional.empty();
    }
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(pathFor(key));
  }

  private Path pathFor(String key) throws IOException {
    if (!FILE_KEY.matcher(key).matches()) {
      throw new IOException("Invalid profile image key");
    }
    return directory.resolve(key);
  }
}
