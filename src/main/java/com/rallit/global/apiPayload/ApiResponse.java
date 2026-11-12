package com.rallit.global.apiPayload;

import com.rallit.global.apiPayload.code.BaseCode;
import com.rallit.global.apiPayload.code.ReasonDTO;
import com.rallit.global.apiPayload.code.status.SuccessStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

	@JsonProperty("isSuccess")
	private final Boolean isSuccess;
	private final String code;
	private final String message;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private T result;

	public static <T> ApiResponse<T> onSuccess(T result) {
		return ApiResponse.<T>builder()
				.isSuccess(true)
				.code(SuccessStatus._OK.getCode())
				.message(SuccessStatus._OK.getMessage())
				.result(result)
				.build();
	}

	public static <T> ApiResponse<T> of(BaseCode code, T result) {
		ReasonDTO reason = code.getReasonHttpStatus();
		return ApiResponse.<T>builder()
				.isSuccess(true)
				.code(reason.getCode())
				.message(reason.getMessage())
				.result(result)
				.build();
	}

	public static <T> ApiResponse<T> onFailure(String code, String message, T result) {
		return ApiResponse.<T>builder()
				.isSuccess(false)
				.code(code)
				.message(message)
				.result(result)
				.build();
	}
}
