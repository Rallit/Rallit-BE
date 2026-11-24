package com.rallit.domain.court.controller.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true,
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
				description = "코트 예약을 찾을 수 없습니다. COURT_SLOT_NOT_FOUND를 반환합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
				description = "검수 대기 중인 증빙이 아닙니다. 미제출·이미 검수한 증빙은 EVIDENCE_REVIEW_NOT_PENDING을 반환합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class)))
})
@interface EvidenceReviewResponses {
}
