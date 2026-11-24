package com.rallit.domain.court.controller.swagger;

import com.rallit.domain.court.dto.EvidenceRejectRequest;
import com.rallit.domain.court.dto.EvidenceReviewItemResponse;
import com.rallit.domain.court.dto.EvidenceReviewResponse;
import com.rallit.domain.court.enums.EvidenceReviewStatus;
import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 인증이 필요합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 필요합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class)))
})
public interface AdminEvidenceReviewSwagger {

	@Operation(summary = "예약 증빙 검수 목록", description = "검수 상태별 코트 예약을 제출 순서대로 조회합니다. 기본은 검수 대기입니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	ResponseEntity<ApiResponse<PageResponse<EvidenceReviewItemResponse>>> list(
			@Parameter(description = "조회할 증빙 검수 상태") EvidenceReviewStatus evidenceReviewStatus,
			@Min(0) @Max(10000) int page,
			@Min(1) @Max(50) int size);

	@Operation(summary = "예약 증빙 이미지 URL 조회", description = "운영자에게만 증빙 원본을 볼 수 있는 만료형 URL을 발급합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
			description = "코트 예약(COURT_SLOT_NOT_FOUND) 또는 제출된 증빙(EVIDENCE_NOT_SUBMITTED)이 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
			description = "이미지 URL을 일시적으로 발급할 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	ResponseEntity<ApiResponse<ImageLinkResponse>> evidenceImage(@Positive Long courtSlotId);

	@Operation(summary = "예약 증빙 승인",
			description = "검수 대기 중인 증빙을 승인합니다. 매치 모집 상태는 바뀌지 않으며 매치에 인증 배지가 표시됩니다.")
	@EvidenceReviewResponses
	ResponseEntity<ApiResponse<EvidenceReviewResponse>> approve(@Positive Long courtSlotId);

	@Operation(summary = "예약 증빙 반려",
			description = "검수 대기 중인 증빙을 반려하고 사유를 저장합니다. 매치는 반려 사유와 함께 취소(CANCELED)되고, "
					+ "예약 시간대가 풀려 같은 시간으로 다시 매치를 개설할 수 있습니다.")
	@EvidenceReviewResponses
	ResponseEntity<ApiResponse<EvidenceReviewResponse>> reject(@Positive Long courtSlotId,
			@Valid EvidenceRejectRequest request);
}
