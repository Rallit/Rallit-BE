package com.rallit.domain.match.repository;

import org.hibernate.exception.ConstraintViolationException;

public final class MatchApplicationConstraintViolations {

	private static final String ACTIVE_APPLICATION_CONSTRAINT = "uk_match_application_active";
	private static final String UNIQUE_VIOLATION = "23505";

	private MatchApplicationConstraintViolations() {
	}

	public static boolean isActiveApplicationConflict(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& ACTIVE_APPLICATION_CONSTRAINT.equals(violation.getConstraintName())
					&& UNIQUE_VIOLATION.equals(violation.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
