package com.rallit.domain.match.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
				description = "서비스 Bearer 토큰 누락, 만료 또는 인증 실패",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "인증 필요", value = MatchDiscoveryExamples.UNAUTHORIZED))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "온보딩을 완료해야 매치를 조회할 수 있습니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "온보딩 미완료", value = MatchDiscoveryExamples.ONBOARDING_REQUIRED))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
				description = "인증 세션 저장소를 일시적으로 사용할 수 없습니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class),
						examples = @ExampleObject(name = "세션 저장소 장애", value = MatchDiscoveryExamples.SERVICE_UNAVAILABLE)))
})
@interface MatchReadErrorResponses {
}
