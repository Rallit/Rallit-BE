package com.rallit.global.apiPayload.code;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ErrorReasonDTO {
	private final Boolean isSuccess;
	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

	@Builder
	public ErrorReasonDTO(Boolean isSuccess, String code, String message, HttpStatus httpStatus) {
		this.isSuccess = isSuccess;
		this.code = code;
		this.message = message;
		this.httpStatus = httpStatus;
	}
}
