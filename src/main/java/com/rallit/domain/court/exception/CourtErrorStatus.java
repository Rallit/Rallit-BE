package com.rallit.domain.court.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CourtErrorStatus implements BaseErrorCode {
	COURT_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료해야 코트 사진을 조회할 수 있습니다."),
	COURT_THUMBNAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "코트 대표 사진을 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	@Override
	public ErrorReasonDTO getReason() {
		return ErrorReasonDTO.builder().isSuccess(false).code(name()).message(message).build();
	}

	@Override
	public ErrorReasonDTO getReasonHttpStatus() {
		return ErrorReasonDTO.builder().isSuccess(false).code(name()).message(message).httpStatus(httpStatus).build();
	}
}
