package com.rallit.domain.image.controller.swagger;

import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true,
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
        description = "이미지 업로드 요청 또는 대상을 찾을 수 없습니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
        description = "아직 이미지가 업로드되지 않았습니다. IMAGE_UPLOAD_NOT_READY를 반환합니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
        description = "이미지 업로드 요청이 만료되었습니다. IMAGE_UPLOAD_EXPIRED를 반환합니다.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiResponse.class)))
})
@interface ImageUploadCompletionResponses {}
