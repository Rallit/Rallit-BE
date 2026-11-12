package com.rallit.domain.user.service;

import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserStatus;
import com.rallit.domain.user.exception.UserErrorStatus;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.storage.ProfileImageStorage;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {
  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final int MAX_DIMENSION = 16_384;
  private static final long MAX_PIXELS = 64_000_000L;
  private static final int VALIDATION_SAMPLE_SIZE = 256;

  private final UserRepository users;
  private final ProfileImageStorage storage;

  @Transactional
  public ProfileImageResponse upload(Long userId, MultipartFile file) {
    String extension = validate(file);
    User user = lockUser(userId);
    String key = UUID.randomUUID() + extension;
    try (var contents = file.getInputStream()) {
      storage.write(key, contents);
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }

    String previous = user.replaceProfileImage(key);
    cleanUpAfterTransaction(previous, key);
    return new ProfileImageResponse(ProfileImageResponse.urlFor(userId));
  }

  @Transactional
  public void delete(Long userId) {
    String previous = lockUser(userId).replaceProfileImage(null);
    cleanUpAfterTransaction(previous, null);
  }

  @Transactional(readOnly = true)
  public Image get(Long userId) {
    try {
      String key = getKey(userId);
      try {
        return readImage(key);
      } catch (NoSuchFileException missing) {
        // A concurrent replacement may have deleted the old file after we read its key.
        String latestKey = getKey(userId);
        if (latestKey.equals(key)) throw missing;
        return readImage(latestKey);
      }
    } catch (NoSuchFileException missing) {
      throw new GeneralException(ErrorStatus._NOT_FOUND);
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

  private String getKey(Long userId) {
    return users.findProfileImageKey(userId, UserStatus.WITHDRAWN)
        .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));
  }

  private Image readImage(String key) throws IOException {
    MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    return new Image(storage.read(key), type);
  }

  private User lockUser(Long userId) {
    return users.findByIdForUpdate(userId).filter(user -> !user.isWithdrawn())
        .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
  }

  private String validate(MultipartFile file) {
    if (file.getSize() > MAX_BYTES) {
      throw new GeneralException(UserErrorStatus.PROFILE_IMAGE_TOO_LARGE);
    }
    String filename = file.getOriginalFilename();
    if (file.isEmpty() || filename == null) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
    }
    return validateImageContents(file, filename.toLowerCase(Locale.ROOT));
  }

  private String validateImageContents(MultipartFile file, String filename) {
    try (var contents = file.getInputStream(); var image = new MemoryCacheImageInputStream(contents)) {
      var readers = ImageIO.getImageReaders(image);
      if (!readers.hasNext()) {
        throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
      }
      var reader = readers.next();
      try {
        reader.setInput(image, true, true);
        String extension = validatedExtension(reader.getFormatName(), filename);
        reader.addIIOReadWarningListener((source, warning) -> {
          throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
        });
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
            || (long) width * height > MAX_PIXELS) {
          throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
        }
        var parameters = reader.getDefaultReadParam();
        int step = Math.ceilDiv(Math.max(width, height), VALIDATION_SAMPLE_SIZE);
        parameters.setSourceSubsampling(step, step, 0, 0);
        reader.read(0, parameters);
        return extension;
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException failure) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE, failure);
    }
  }

  private String validatedExtension(String format, String filename) {
    if ("png".equalsIgnoreCase(format) && filename.endsWith(".png")) return ".png";
    if ("jpeg".equalsIgnoreCase(format) && (filename.endsWith(".jpeg") || filename.endsWith(".jpg"))) {
      return ".jpg";
    }
    throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
  }

  private void cleanUpAfterTransaction(String previous, String uploaded) {
    if (previous == null && uploaded == null) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) deleteQuietly(previous);
        if (status == STATUS_ROLLED_BACK) deleteQuietly(uploaded);
      }
    });
  }

  private void deleteQuietly(String key) {
    if (key == null) return;
    try {
      storage.delete(key);
    } catch (IOException failure) {
      log.warn("Failed to remove an unreferenced profile image", failure);
    }
  }

  public record Image(Resource contents, MediaType mediaType) {}
}
