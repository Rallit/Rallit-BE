package com.rallit.domain.user.service;

import com.rallit.domain.image.ImageKeyUpdate;
import com.rallit.domain.image.ImageUploadPurpose;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.UserStatus;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.S3ImageObjects;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {
  private final UserRepository users;
  private final S3ImageObjects objects;
  private final PlatformTransactionManager transactions;

  public String imageKey(Long userId) {
    return users.findProfileImageKey(userId, UserStatus.WITHDRAWN)
        .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void delete(Long userId) {
    var update = new ImageKeyUpdate();
    try {
      new TransactionTemplate(transactions).executeWithoutResult(status -> {
        update.register();
        update.replaced(lockUser(userId).replaceProfileImage(null));
      });
    } finally {
      deleteQuietly(update.unreferencedKey(null));
    }
  }

  private User lockUser(Long userId) {
    return users.findByIdForUpdate(userId).filter(user -> !user.isWithdrawn())
        .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
  }

  private void deleteQuietly(String key) {
    if (key == null) return;
    try {
      objects.delete(ImageUploadPurpose.PROFILE.getPrefix(), key);
    } catch (IOException failure) {
      log.warn("Failed to remove an unreferenced profile image", failure);
    }
  }
}
