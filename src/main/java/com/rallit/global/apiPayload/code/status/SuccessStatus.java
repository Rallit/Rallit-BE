package com.rallit.global.apiPayload.code.status;

import com.rallit.global.apiPayload.code.BaseCode;
import com.rallit.global.apiPayload.code.ReasonDTO;
import lombok.Getter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {
	_OK(HttpStatus.OK, "200", "성공입니다."),
	_CREATED(HttpStatus.CREATED, "201", "생성되었습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public ReasonDTO getReason() {
		return ReasonDTO.builder()
				.isSuccess(true)
				.code(code)
				.message(message)
				.build();
	}

	@Override
	public ReasonDTO getReasonHttpStatus() {
		return ReasonDTO.builder()
				.isSuccess(true)
				.code(code)
				.message(message)
				.httpStatus(httpStatus)
				.build();
	}
}
