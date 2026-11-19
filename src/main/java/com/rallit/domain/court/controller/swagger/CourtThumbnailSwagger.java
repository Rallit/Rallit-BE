package com.rallit.domain.court.controller.swagger;

import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "코트")
@SecurityRequirement(name = "bearerAuth")
public interface CourtThumbnailSwagger {

	@Operation(summary = "코트 대표 사진 URL 조회", description = "온보딩을 완료한 회원에게 이미지 URL과 만료 시각을 반환합니다.")
	@ApiResponses({
	    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
	    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
	        description = "회원 인증이 필요합니다.",
	        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	            schema = @Schema(implementation = ApiResponse.class))),
	    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
	        description = "온보딩을 완료해야 코트 사진을 조회할 수 있습니다.",
	        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	            schema = @Schema(implementation = ApiResponse.class))),
	    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
	        description = "코트 대표 사진을 찾을 수 없습니다.",
	        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	            schema = @Schema(implementation = ApiResponse.class))),
	    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
	        description = "이미지 URL을 일시적으로 발급할 수 없습니다.",
	        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	            schema = @Schema(implementation = ApiResponse.class)))
	})
	ResponseEntity<ApiResponse<ImageLinkResponse>> getCourtThumbnailUrl(
			UserDetails principal,
			Long courtId);
}
