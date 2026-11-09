package com.rallit.domain.auth.model;

import java.util.Locale;
import java.util.Objects;

public record SocialIdentity(
    OAuthProvider provider, String subject, String email, boolean emailVerified) {
  public SocialIdentity {
    Objects.requireNonNull(provider, "Missing provider");
    if (subject == null || subject.isBlank())
      throw new IllegalArgumentException("Missing provider identity");
    email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    if (email != null && (email.isBlank() || email.length() > 320 || !email.contains("@")))
      email = null;
  }

  public String verifiedEmail() {
    return emailVerified ? email : null;
  }

  @Override
  public String toString() {
    return "SocialIdentity[provider=" + provider + "]";
  }
}
