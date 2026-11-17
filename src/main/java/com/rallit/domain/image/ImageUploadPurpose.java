package com.rallit.domain.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageUploadPurpose {
  PROFILE("profile-images/"),
  COURT("court-thumbnails/");

  private final String prefix;
}
