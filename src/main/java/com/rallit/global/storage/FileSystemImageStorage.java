package com.rallit.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

public class FileSystemImageStorage {
  private final Path directory;

  public FileSystemImageStorage(String directory) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

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

  public Optional<Resource> read(String key) throws IOException {
    try {
      return Optional.of(new InputStreamResource(Files.newInputStream(pathFor(key))));
    } catch (NoSuchFileException missing) {
      return Optional.empty();
    }
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(pathFor(key));
  }

  private Path pathFor(String key) throws IOException {
    return directory.resolve(ImageKey.requireValid(key));
  }
}
