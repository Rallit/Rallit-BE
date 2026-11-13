package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MatchApplicationErrorStatus implements BaseErrorCode {

	ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "APPLICATION403", "온보딩을 완료한 회원만 참가 신청할 수 있습니다."),
	HOST_NOT_ALLOWED(HttpStatus.FORBIDDEN, "APPLICATION403_1", "본인이 개설한 매치에는 신청할 수 없습니다."),
	GENDER_NOT_RECRUITED(HttpStatus.FORBIDDEN, "APPLICATION403_2", "모집 대상 성별이 아닙니다."),
	APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION404", "참가 신청 내역을 찾을 수 없습니다."),
	APPLICATION_DUPLICATED(HttpStatus.CONFLICT, "APPLICATION409", "이미 진행 중인 참가 신청이 있습니다."),
	APPLICATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "APPLICATION409_1", "현재 상태에서는 참가 신청을 취소할 수 없습니다.");

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
