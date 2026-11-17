package com.rallit.domain.image.service;

import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.PresignedImageStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageLinkService {
  private final PresignedImageStorage presignedStorage;

  public ImageLinkResponse link(ImageUploadPurpose purpose, String key) {
    try {
      var signed = presignedStorage.signRead(purpose.getPrefix(), key);
      return new ImageLinkResponse(signed.url(), signed.expiresAt());
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

}
