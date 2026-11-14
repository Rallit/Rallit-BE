package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MatchApplicationErrorStatus implements BaseErrorCode {

	ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료한 회원만 참가 신청할 수 있습니다."),
	HOST_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인이 개설한 매치에는 신청할 수 없습니다."),
	GENDER_NOT_RECRUITED(HttpStatus.FORBIDDEN, "모집 대상 성별이 아닙니다."),
	APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참가 신청 내역을 찾을 수 없습니다."),
	APPLICATION_DUPLICATED(HttpStatus.CONFLICT, "이미 진행 중인 참가 신청이 있습니다."),
	APPLICATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "현재 상태에서는 참가 신청을 취소할 수 없습니다."),
	RECRUITMENT_FULL(HttpStatus.CONFLICT, "해당 성별의 모집 인원이 모두 확정되었습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	@Override
	public ErrorReasonDTO getReason() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(name())
				.message(message)
				.build();
	}

	@Override
	public ErrorReasonDTO getReasonHttpStatus() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(name())
				.message(message)
				.httpStatus(httpStatus)
				.build();
	}
}
