package com.rallit.domain.match.controller.swagger;

import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.match.dto.request.MatchCreateRequest;
import com.rallit.domain.match.dto.response.MatchCreateResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "매치 개설", description = "코트 예약 정보와 모집 조건으로 매치를 개설하고 예약 증빙 사진을 업로드합니다.")
@SecurityRequirement(name = "bearerAuth")
public interface MatchCreationSwagger {

	@Operation(summary = "예약 증빙 업로드 발급",
			description = """
					코트 예약 증빙 사진(최대 1장)을 S3에 올릴 URL과 서명 폼을 반환합니다. 증빙은 선택 사항입니다.

					발급 후 업로드 URL 만료 전에 S3 업로드와 매치 개설 요청을 마쳐야 하므로, 매치 개설 버튼을 누를 때 발급·업로드한 뒤
					`uploadId`를 `reservation.evidenceUploadId`로 전달해 주세요. 매치 개설이 성공해야 증빙이 저장됩니다.
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "파일 이름, 형식 또는 크기가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
			description = "온보딩을 완료해야 매치를 개설할 수 있습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
			description = "이미지 크기가 5MiB를 초과합니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
			description = "이미지 저장소 장애입니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	ResponseEntity<ApiResponse<ImageUploadResponse>> initiateEvidenceUpload(
			@Parameter(hidden = true) UserDetails principal,
			@Valid ImageUploadRequest request);

	@Operation(summary = "매치 개설",
			description = """
					코트 예약 정보와 모집 조건으로 코트 예약과 매치를 한 번에 저장하고 즉시 모집을 시작합니다.

					- 호스트는 참가 인원에 포함됩니다. 전체 인원은 `1 + 남성 모집 인원 + 여성 모집 인원`이며 2~6명입니다.
					- 모집 마감은 기존 신청 기한과 같은 경기 시작 1시간 전으로 정합니다.
					- 예약 날짜는 오늘(서울 기준)부터 14일 뒤까지 선택할 수 있습니다.
					- 예약 증빙은 선택 사항입니다. 증빙을 첨부하면 `PENDING`, 생략하면 `NOT_SUBMITTED`로 저장하며,
					  매치는 바로 `RECRUITING` 상태로 모집합니다.
					- 같은 코트·코트 번호에 겹치는 시간대 예약이 있으면 409를 반환합니다. 중복 요청도 같은 방식으로 막습니다.
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "입력값(COMMON400), 예약 날짜(MATCH_RESERVATION_DATE_OUT_OF_RANGE), "
					+ "예약 시간(MATCH_INVALID_SCHEDULE, MATCH_START_TOO_SOON), "
					+ "모집 인원(MATCH_INVALID_RECRUIT_COUNT), 성별 조건(MATCH_SAME_GENDER_CONFLICT, "
					+ "MATCH_TYPE_GENDER_CONFLICT), NTRP(MATCH_INVALID_RECRUITMENT_NTRP) 또는 증빙 이미지가 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "회원 인증이 필요합니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
			description = "온보딩을 완료해야 매치를 개설할 수 있습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
			description = "코트(COURT_NOT_FOUND) 또는 본인의 증빙 업로드 요청(IMAGE_UPLOAD_NOT_FOUND)을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
			description = "겹치는 예약(COURT_SLOT_OVERLAPPED), 업로드 전(IMAGE_UPLOAD_NOT_READY) 또는 "
					+ "이미 사용한 증빙(IMAGE_UPLOAD_ALREADY_USED)입니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
			description = "증빙 업로드 요청이 만료되었습니다. 사진을 다시 올려 주세요.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	ResponseEntity<ApiResponse<MatchCreateResponse>> create(
			@Parameter(hidden = true) UserDetails principal,
			@Valid MatchCreateRequest request);
}
