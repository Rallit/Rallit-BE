package com.rallit.domain.image;

import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.PresignedImageStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageLinkService {
  private final ObjectProvider<PresignedImageStorage> presignedStorage;

  public ImageLinkResponse link(ImageUploadPurpose purpose, String key, String relativePath) {
    var presigned = presignedStorage.getIfAvailable();
    if (presigned == null) return new ImageLinkResponse(relativePath, null);
    try {
      var signed = presigned.signRead(purpose.getPrefix(), key);
      return new ImageLinkResponse(signed.url(), signed.expiresAt());
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

  public void requireFilesystem() {
    if (presignedStorage.getIfAvailable() != null) {
      throw new GeneralException(ErrorStatus._NOT_FOUND);
    }
  }
}
