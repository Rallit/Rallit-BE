package com.rallit.domain.image;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
enum ImageUploadErrorStatus implements BaseErrorCode {
  IMAGE_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지 업로드 요청을 찾을 수 없습니다."),
  IMAGE_UPLOAD_EXPIRED(HttpStatus.GONE, "이미지 업로드 요청이 만료되었습니다."),
  IMAGE_UPLOAD_NOT_READY(HttpStatus.CONFLICT, "이미지를 업로드한 후 완료해 주세요."),
  IMAGE_UPLOAD_INVALID_FILE(HttpStatus.BAD_REQUEST, "파일 이름, 형식과 크기를 확인해 주세요.");

  private final HttpStatus status;
  private final String message;

  @Override
  public ErrorReasonDTO getReason() {
    return ErrorReasonDTO.builder().isSuccess(false).code(name()).message(message).build();
  }

  @Override
  public ErrorReasonDTO getReasonHttpStatus() {
    return ErrorReasonDTO.builder().isSuccess(false).code(name()).message(message).httpStatus(status).build();
  }
}
