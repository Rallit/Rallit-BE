package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.exception.GeneralException;

public class MatchApplicationNotPendingException extends GeneralException {

	public MatchApplicationNotPendingException() {
		super(MatchApplicationErrorStatus.APPLICATION_NOT_PENDING);
	}
}
