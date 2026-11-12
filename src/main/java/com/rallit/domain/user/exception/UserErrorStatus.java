package com.rallit.domain.user.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum UserErrorStatus implements BaseErrorCode {
  INVALID_ACTIVITY_AREA(HttpStatus.BAD_REQUEST, "활동 지역을 확인해 주세요."),
  INVALID_MATCH_PREFERENCES(HttpStatus.BAD_REQUEST, "동성만 매칭과 혼합복식은 함께 선택할 수 없습니다."),
  NICKNAME_CONFLICT(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
  ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 온보딩을 완료했습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public ErrorReasonDTO getReason() {
    return reason().build();
  }

  @Override
  public ErrorReasonDTO getReasonHttpStatus() {
    return reason().httpStatus(status).build();
  }

  private ErrorReasonDTO.ErrorReasonDTOBuilder reason() {
    return ErrorReasonDTO.builder().isSuccess(false).code("COMMON" + status.value()).message(message);
  }
}
