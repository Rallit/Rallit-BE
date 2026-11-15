package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.exception.GeneralException;

public class InvalidMatchNtrpException extends GeneralException {

	public InvalidMatchNtrpException() {
		super(MatchErrorStatus.MATCH_INVALID_RECRUITMENT_NTRP);
	}
}
