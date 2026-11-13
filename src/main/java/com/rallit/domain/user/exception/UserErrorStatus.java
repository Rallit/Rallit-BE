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
  ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 온보딩을 완료했습니다."),
  INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "JPG, JPEG 또는 PNG 사진을 선택해 주세요."),
  PROFILE_IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.BAD_REQUEST,
      "프로필 사진은 가로·세로 각각 4,096픽셀, 총 1,250만 픽셀까지 올릴 수 있습니다."),
  PROFILE_IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "프로필 사진은 최대 5MB까지 올릴 수 있습니다.");

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
