package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MatchErrorStatus implements BaseErrorCode {

	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "매치를 찾을 수 없습니다."),
	MATCH_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료한 회원만 매치를 조회할 수 있습니다."),
	MATCH_DATE_REQUIRED_FOR_TIME_FILTER(HttpStatus.BAD_REQUEST, "시간 조건을 사용하려면 조회 날짜를 지정해 주세요."),
	MATCH_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "조회 시작 시간은 종료 시간보다 빨라야 합니다."),
	MATCH_PAGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "요청한 페이지가 매치 목록의 범위를 벗어났습니다."),
	MATCH_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 매치가 아닙니다."),
	MATCH_RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "모집 기한이 지난 매치입니다.");

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
