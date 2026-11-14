package com.rallit.domain.match.exception;

import com.rallit.domain.match.enums.MatchApplicationStatus;

public class MatchApplicationNotCancelableException extends RuntimeException {

	public MatchApplicationNotCancelableException(MatchApplicationStatus status) {
		super("Cannot cancel match application in " + status + " status");
	}
}
