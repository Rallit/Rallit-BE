package com.rallit.domain.user.repository;

import org.hibernate.exception.ConstraintViolationException;

public final class UserConstraintViolations {
  private static final String NICKNAME_CONSTRAINT = "uk_users_nickname";
  private static final String UNIQUE_VIOLATION = "23505";

  private UserConstraintViolations() {}

  public static boolean isNicknameConflict(Throwable failure) {
    return matches(failure, NICKNAME_CONSTRAINT);
  }

  public static boolean isEmailConflict(Throwable failure) {
    return matches(failure, "uk_users_email");
  }

  private static boolean matches(Throwable failure, String constraint) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && constraint.equals(violation.getConstraintName())
          && UNIQUE_VIOLATION.equals(violation.getSQLState())) return true;
    }
    return false;
  }
}
