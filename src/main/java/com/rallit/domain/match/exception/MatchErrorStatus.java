package com.rallit.domain.match.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MatchErrorStatus implements BaseErrorCode {

	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "매치를 찾을 수 없습니다."),
	MATCH_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료한 회원만 매치를 조회할 수 있습니다."),
	MATCH_REQUEST_ONBOARDING_REQUIRED(MATCH_ONBOARDING_REQUIRED, "온보딩을 완료해야 매치 요청 기능을 이용할 수 있습니다."),
	MATCH_DATE_REQUIRED_FOR_TIME_FILTER(HttpStatus.BAD_REQUEST, "시간 조건을 사용하려면 조회 날짜를 지정해 주세요."),
	MATCH_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "조회 시작 시간은 종료 시간보다 빨라야 합니다."),
	MATCH_INVALID_SEARCH_AREA(HttpStatus.BAD_REQUEST, "지원하지 않는 자치구가 포함되어 있습니다."),
	MATCH_INVALID_SEARCH_NTRP(HttpStatus.BAD_REQUEST, "검색 NTRP는 1.0부터 4.0+까지 0.5 단위로 최솟값과 최댓값을 순서대로 입력해 주세요."),
	MATCH_INVALID_RECRUITMENT_NTRP(HttpStatus.BAD_REQUEST, "모집 NTRP는 1.0부터 4.0+까지 0.5 단위로 선택해야 합니다."),
	MATCH_PAGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "요청한 페이지가 매치 목록의 범위를 벗어났습니다."),
	MATCH_REQUEST_INVALID_DATE(HttpStatus.BAD_REQUEST, "매칭 요청 날짜는 오늘부터 14일 이내로 선택해 주세요."),
	MATCH_REQUEST_PAST_START_TIME(HttpStatus.BAD_REQUEST, "오늘만 선택한 매칭 요청은 현재 시각보다 늦은 시작 시각을 선택해 주세요."),
	MATCH_REQUEST_INVALID_PREFERENCES(HttpStatus.BAD_REQUEST, "동성만 모집하는 매치를 찾을 때는 혼합복식을 선택할 수 없습니다."),
	MATCH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "매칭 요청을 찾을 수 없습니다."),
	MATCH_REQUEST_LIMIT_REACHED(HttpStatus.CONFLICT, "활성 매칭 요청을 최대 3개까지 저장할 수 있습니다."),
	MATCH_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 매치가 아닙니다."),
	MATCH_RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "모집 기한이 지난 매치입니다.");

	private final HttpStatus httpStatus;
	private final String message;
	private final String responseCode;

	MatchErrorStatus(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
		this.responseCode = name();
	}

	MatchErrorStatus(MatchErrorStatus existing, String message) {
		this.httpStatus = existing.httpStatus;
		this.message = message;
		this.responseCode = existing.responseCode;
	}

	@Override
	public ErrorReasonDTO getReason() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(responseCode)
				.message(message)
				.build();
	}

	@Override
	public ErrorReasonDTO getReasonHttpStatus() {
		return ErrorReasonDTO.builder()
				.isSuccess(false)
				.code(responseCode)
				.message(message)
				.httpStatus(httpStatus)
				.build();
	}
}
