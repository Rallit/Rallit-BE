package com.rallit.global.apiPayload.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void generalExceptionUsesStatusAndCodeOfErrorStatus() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.onThrowException(new GeneralException(ErrorStatus._NOT_FOUND));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().getIsSuccess()).isFalse();
		assertThat(response.getBody().getCode()).isEqualTo("COMMON404");
	}

	@Test
	void unexpectedExceptionDoesNotExposeInternalMessage() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.exception(new IllegalStateException("db password leaked"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getCode()).isEqualTo("COMMON500");
		assertThat(response.getBody().getMessage()).doesNotContain("db password leaked");
	}
}
