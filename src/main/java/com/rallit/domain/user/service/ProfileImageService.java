package com.rallit.domain.user.service;

import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserStatus;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.storage.ProfileImageStorage;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.ImageKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {
  private final UserRepository users;
  private final ProfileImageStorage storage;
  private final ProfileImageNormalizer normalizer;
  private final PlatformTransactionManager transactions;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProfileImageResponse upload(Long userId, MultipartFile file) {
    var image = normalizer.normalize(file);
    String key = UUID.randomUUID() + image.extension();
    byte[] contents = image.contents();
    try {
      storage.write(key, new ByteArrayInputStream(contents), contents.length, ImageKey.mediaType(key));
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }

    replaceImageKey(userId, key);
    return new ProfileImageResponse(ProfileImageResponse.urlFor(userId));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void delete(Long userId) {
    replaceImageKey(userId, null);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Image get(Long userId) {
    try {
      return readImageWithReplacementRetry(userId)
          .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

  private Optional<Image> readImageWithReplacementRetry(Long userId) throws IOException {
    String key = getKey(userId);
    var image = readImage(key);
    if (image.isPresent()) return image;

    // A concurrent replacement may have deleted the old file after we read its key.
    String latestKey = getKey(userId);
    return latestKey.equals(key) ? Optional.empty() : readImage(latestKey);
  }

  private String getKey(Long userId) {
    return users.findProfileImageKey(userId, UserStatus.WITHDRAWN)
        .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));
  }

  private Optional<Image> readImage(String key) throws IOException {
    return storage.read(key).map(contents -> new Image(contents, ImageKey.mediaType(key)));
  }

  private void replaceImageKey(Long userId, String key) {
    var update = new ImageKeyUpdate();
    try {
      new TransactionTemplate(transactions).executeWithoutResult(status -> {
        update.register();
        update.previous = lockUser(userId).replaceProfileImage(key);
      });
    } finally {
      deleteQuietly(update.unreferencedKey(key));
    }
  }

  private User lockUser(Long userId) {
    return users.findByIdForUpdate(userId).filter(user -> !user.isWithdrawn())
        .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
  }

  private static final class ImageKeyUpdate implements TransactionSynchronization {
    private String previous;
    // Before the update starts, the new file is unreferenced.
    private int completionStatus = STATUS_ROLLED_BACK;

    private void register() {
      TransactionSynchronizationManager.registerSynchronization(this);
      completionStatus = STATUS_UNKNOWN;
    }

    @Override
    public void afterCompletion(int status) {
      completionStatus = status;
    }

    private String unreferencedKey(String uploaded) {
      return switch (completionStatus) {
        case STATUS_COMMITTED -> previous;
        case STATUS_ROLLED_BACK -> uploaded;
        default -> null;
      };
    }
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
