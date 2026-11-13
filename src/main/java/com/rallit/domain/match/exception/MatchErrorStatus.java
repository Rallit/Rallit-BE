package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MatchErrorStatus implements BaseErrorCode {

	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH404", "매치를 찾을 수 없습니다."),
	MATCH_NOT_RECRUITING(HttpStatus.CONFLICT, "MATCH409", "모집 중인 매치가 아닙니다."),
	MATCH_RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "MATCH409_1", "모집 기한이 지난 매치입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public ErrorReasonDTO getReason() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(code)
				.message(message)
				.build();
	}

	@Override
	public ErrorReasonDTO getReasonHttpStatus() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(code)
				.message(message)
				.httpStatus(httpStatus)
				.build();
	}
}
