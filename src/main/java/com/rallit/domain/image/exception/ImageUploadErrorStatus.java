package com.rallit.domain.image.exception;

import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import org.springframework.http.HttpStatus;

public enum ImageUploadErrorStatus implements BaseErrorCode {
  IMAGE_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지 업로드 요청을 찾을 수 없습니다."),
  IMAGE_UPLOAD_EXPIRED(HttpStatus.GONE, "이미지 업로드 요청이 만료되었습니다."),
  IMAGE_UPLOAD_NOT_READY(HttpStatus.CONFLICT, "이미지를 업로드한 후 완료해 주세요."),
  IMAGE_UPLOAD_INVALID_FILE(HttpStatus.BAD_REQUEST, "파일 이름, 형식과 크기를 확인해 주세요."),
  INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "JPG, JPEG 또는 PNG 사진을 선택해 주세요.", "COMMON400"),
  IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.BAD_REQUEST,
      "사진은 가로·세로 각각 4,096픽셀, 총 1,250만 픽셀까지 올릴 수 있습니다.", "COMMON400");

  private final HttpStatus status;
  private final String message;
  private final String code;

  ImageUploadErrorStatus(HttpStatus status, String message) {
    this(status, message, null);
  }

  ImageUploadErrorStatus(HttpStatus status, String message, String code) {
    this.status = status;
    this.message = message;
    this.code = code == null ? name() : code;
  }

  @Override
  public ErrorReasonDTO getReason() {
    return reason().build();
  }

  @Override
  public ErrorReasonDTO getReasonHttpStatus() {
    return reason().httpStatus(status).build();
  }

  private ErrorReasonDTO.ErrorReasonDTOBuilder reason() {
    return ErrorReasonDTO.builder().isSuccess(false).code(code).message(message);
  }
}
