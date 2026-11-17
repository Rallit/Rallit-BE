package com.rallit.domain.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageUploadPurpose {
  PROFILE("profile-images/", 320, 320),
  COURT("court-thumbnails/", 1440, 810);

  private final String prefix;
  private final int outputWidth;
  private final int outputHeight;
}
