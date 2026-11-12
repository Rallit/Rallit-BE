package com.rallit.global.apiPayload.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

	private final BaseErrorCode code;

	public GeneralException(BaseErrorCode code) {
		super(code.getReason().getMessage());
		this.code = code;
	}

	public GeneralException(BaseErrorCode code, Throwable cause) {
		super(code.getReason().getMessage(), cause);
		this.code = code;
	}

	public ErrorReasonDTO getErrorReason() {
		return code.getReason();
	}

	public ErrorReasonDTO getErrorReasonHttpStatus() {
		return code.getReasonHttpStatus();
	}
}
