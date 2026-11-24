package com.rallit.domain.court.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CourtRegisterRequest(
		@Schema(description = "코트 검색 결과의 kakaoPlaceId", example = "8152837")
		@NotNull @Pattern(regexp = "[0-9]{1,20}") String kakaoPlaceId,
		@Schema(description = "코트 검색 결과의 name. 카카오 장소를 다시 조회하는 데만 사용합니다.", example = "장충테니스장")
		@NotBlank @Size(max = 100) String name,
		@Schema(description = "코트 검색 결과의 latitude", example = "37.558651")
		@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
		@Schema(description = "코트 검색 결과의 longitude", example = "127.004377")
		@NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
}
