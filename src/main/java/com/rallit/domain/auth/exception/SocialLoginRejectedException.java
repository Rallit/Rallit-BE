package com.rallit.domain.auth.exception;

import com.rallit.domain.auth.model.OAuthProvider;

public class SocialLoginRejectedException extends RuntimeException {
  public enum Reason {
    EMAIL_REQUIRED,
    ACCOUNT_EXISTS
  }

  private final Reason reason;
  private final OAuthProvider existingProvider;

  public SocialLoginRejectedException(Reason reason, OAuthProvider existingProvider) {
    super(reason.name());
    this.reason = reason;
    this.existingProvider = existingProvider;
  }

  public Reason reason() {
    return reason;
  }

  public OAuthProvider existingProvider() {
    return existingProvider;
  }
}
