package com.rallit.domain.court.repository;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;

public final class CourtConstraintViolations {

	private static final String SLOT_OVERLAP_CONSTRAINT = "ex_court_slot_overlap";
	private static final String EXCLUSION_VIOLATION = "23P01";
	private static final String KAKAO_PLACE_CONSTRAINT = "uk_court_kakao_place_id";
	private static final String UNIQUE_VIOLATION = "23505";

	private CourtConstraintViolations() {
	}

	public static boolean isSlotOverlap(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())
					&& sql.getMessage() != null && sql.getMessage().contains(SLOT_OVERLAP_CONSTRAINT)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isKakaoPlaceConflict(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& KAKAO_PLACE_CONSTRAINT.equals(violation.getConstraintName())
					&& UNIQUE_VIOLATION.equals(violation.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
