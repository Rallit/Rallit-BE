package com.rallit.domain.image.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageUploadPurpose {
  PROFILE("profile-images/", 320, 320, true),
  COURT("court-thumbnails/", 1440, 810, true),
  RESERVATION_EVIDENCE("reservation-evidence/", 2048, 2048, false);

  private final String prefix;
  private final int outputWidth;
  private final int outputHeight;
  private final boolean cropped;
}
