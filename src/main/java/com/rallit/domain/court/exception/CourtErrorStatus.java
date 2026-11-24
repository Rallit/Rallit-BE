package com.rallit.domain.court.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CourtErrorStatus implements BaseErrorCode {
	COURT_NOT_FOUND(HttpStatus.NOT_FOUND, "코트를 찾을 수 없습니다."),
	COURT_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료해야 코트 사진을 조회할 수 있습니다."),
	COURT_THUMBNAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "코트 대표 사진을 찾을 수 없습니다."),
	COURT_SEARCH_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 완료해야 코트를 검색하거나 등록할 수 있습니다."),
	COURT_SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력하거나 현재 위치를 함께 보내 주세요."),
	COURT_SEARCH_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "위치 기반 검색에는 위도와 경도를 함께 입력해 주세요."),
	COURT_SEARCH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "코트 검색 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
	COURT_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "코트 검색 서비스를 일시적으로 이용할 수 없습니다."),
	COURT_PLACE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "선택한 장소를 확인할 수 없습니다. 코트를 다시 검색해 주세요."),
	COURT_UNSUPPORTED_AREA(HttpStatus.BAD_REQUEST, "아직 서비스하지 않는 지역의 코트입니다."),
	COURT_SLOT_OVERLAPPED(HttpStatus.CONFLICT, "같은 코트 번호에 겹치는 시간대의 예약이 이미 등록되어 있습니다.");

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
