package com.rallit.domain.user.exception;

public class OnboardingProfileValidationException extends RuntimeException {
  private final Reason reason;

  public OnboardingProfileValidationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    INVALID_REQUIRED_VALUES,
    INVALID_ACTIVITY_AREA,
    INVALID_MATCH_PREFERENCES
  }
}
