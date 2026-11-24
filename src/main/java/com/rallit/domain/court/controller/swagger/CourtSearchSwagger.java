package com.rallit.domain.court.controller.swagger;

import java.math.BigDecimal;

import com.rallit.domain.court.dto.CourtPlaceResponse;
import com.rallit.domain.court.dto.CourtRegisterRequest;
import com.rallit.domain.court.dto.CourtResponse;
import com.rallit.domain.court.enums.CourtSearchSort;
import com.rallit.domain.court.service.CourtSearchService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "코트")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "회원 인증이 필요합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "온보딩을 완료해야 합니다. COURT_SEARCH_ONBOARDING_REQUIRED를 반환합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
				description = "회원별 분당 카카오 장소 조회 한도를 넘었습니다. COURT_SEARCH_RATE_LIMITED를 반환합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
				description = "카카오 장소 검색 장애 또는 응답 지연입니다. COURT_SEARCH_UNAVAILABLE을 반환합니다.",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ApiResponse.class)))
})
public interface CourtSearchSwagger {

	@Operation(summary = "코트 장소 검색",
			description = """
					카카오 키워드 장소 검색으로 예약한 코트를 찾습니다.

					- 좌표 없이 `keyword`만 보내면 일반 키워드 검색을 합니다.
					- `latitude`, `longitude`를 함께 보내면 반경(`radius`, 기본 5km) 안에서 검색하고 기본으로 가까운 순 정렬합니다.
					- 좌표만 보내고 `keyword`를 생략하면 `테니스장`으로 근처 코트를 추천합니다.
					- 서버는 사용자의 위치를 추정하지 않습니다. 브라우저 위치 권한으로 얻은 좌표만 전달해 주세요.
					- 검색 결과가 없으면 빈 목록을 반환합니다.
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "좌표·반경·정렬 조합 또는 입력값이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	ResponseEntity<ApiResponse<PageResponse<CourtPlaceResponse>>> search(
			@Parameter(hidden = true) UserDetails principal,
			@Parameter(description = "코트 이름 또는 주소 키워드. 좌표가 있으면 생략할 수 있습니다.", example = "장충")
			@Size(max = 100) String keyword,
			@Parameter(description = "사용자 위도(WGS84). longitude와 함께 보냅니다.", example = "37.558651")
			@DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
			@Parameter(description = "사용자 경도(WGS84). latitude와 함께 보냅니다.", example = "127.004377")
			@DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
			@Parameter(description = "검색 반경(m). 좌표가 있을 때만 사용하며 기본 5000입니다.", example = "5000")
			@Min(1) @Max(20000) Integer radius,
			@Parameter(description = "ACCURACY 정확도순, DISTANCE 거리순. 좌표가 있으면 기본 DISTANCE, 없으면 ACCURACY입니다.")
			CourtSearchSort sort,
			@Parameter(description = "0부터 시작하는 페이지")
			@Min(0) @Max(CourtSearchService.MAX_PAGE) int page,
			@Parameter(description = "페이지당 최대 항목 수")
			@Min(1) @Max(15) int size);

	@Operation(summary = "코트 등록",
			description = """
					검색 결과에서 선택한 카카오 장소를 Rallit 코트로 등록하고 `courtId`를 반환합니다.

					- 이미 등록된 장소면 기존 코트를 200으로 반환하고, 새로 등록하면 201을 반환합니다.
					- 서버가 카카오에서 장소를 다시 조회해 확인한 이름·주소·좌표만 저장합니다.
					- 주차·샤워 등 부가 정보는 저장하지 않으며 운영자가 따로 관리합니다.
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 등록된 코트",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새로 등록한 코트",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "입력값이 유효하지 않거나(COMMON400), 카카오에서 장소를 확인할 수 없거나(COURT_PLACE_NOT_VERIFIED), "
					+ "서비스 지역 밖의 코트입니다(COURT_UNSUPPORTED_AREA).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ApiResponse.class)))
	ResponseEntity<ApiResponse<CourtResponse>> register(
			@Parameter(hidden = true) UserDetails principal,
			@Valid CourtRegisterRequest request);
}
