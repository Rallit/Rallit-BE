package com.rallit.global.apiPayload.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.ErrorReasonDTO;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(GeneralException.class)
	public ResponseEntity<ApiResponse<Void>> onThrowException(GeneralException exception) {
		ErrorReasonDTO reason = exception.getErrorReasonHttpStatus();
		if (reason.getHttpStatus().is5xxServerError()) {
			log.error("Application request processing error", exception);
		}
		return ResponseEntity.status(reason.getHttpStatus())
				.body(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), null));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> validation(
			ConstraintViolationException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach(violation -> errors.merge(
				violation.getPropertyPath().toString(), violation.getMessage(),
				(existing, additional) -> existing + ", " + additional));
		ErrorReasonDTO reason = ErrorStatus._BAD_REQUEST.getReasonHttpStatus();
		return ResponseEntity.status(reason.getHttpStatus())
				.body(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), errors));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> exception(Exception exception) {
		log.error("Unexpected request processing error", exception);
		ErrorReasonDTO reason = ErrorStatus._INTERNAL_SERVER_ERROR.getReasonHttpStatus();
		return ResponseEntity.status(reason.getHttpStatus())
				.body(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), null));
	}

	@Override
	public ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ObjectError error : exception.getBindingResult().getAllErrors()) {
			String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
			String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "유효하지 않은 값입니다.";
			errors.merge(field, message, (existing, additional) -> existing + ", " + additional);
		}
		ErrorReasonDTO reason = ErrorStatus._BAD_REQUEST.getReasonHttpStatus();
		ApiResponse<Map<String, String>> body = ApiResponse.onFailure(reason.getCode(), reason.getMessage(), errors);
		return handleExceptionInternal(exception, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception exception, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("Spring MVC request processing error", exception);
		}
		Object response = body instanceof ApiResponse<?> ? body
				: ApiResponse.onFailure("COMMON" + status.value(), errorMessage(status), null);
		return super.handleExceptionInternal(exception, response, headers, status, request);
	}

	private String errorMessage(HttpStatusCode httpStatus) {
		for (ErrorStatus status : ErrorStatus.values()) {
			ErrorReasonDTO reason = status.getReasonHttpStatus();
			if (reason.getHttpStatus().value() == httpStatus.value()) {
				return reason.getMessage();
			}
		}
		return httpStatus.is5xxServerError()
				? ErrorStatus._INTERNAL_SERVER_ERROR.getMessage() : "요청을 처리할 수 없습니다.";
	}
}
