package com.rallit.domain.match.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.rallit.domain.user.enums.PreferredMatchType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MatchSearchRequest(
		@ArraySchema(arraySchema = @Schema(description = "경기 시작일 목록(서울 기준). 1개 이상 최대 14개.",
				requiredMode = Schema.RequiredMode.REQUIRED),
				schema = @Schema(type = "string", format = "date", example = "2026-11-21"),
				minItems = 1, maxItems = 14)
		@NotEmpty @Size(max = 14) List<@NotNull @Pattern(regexp = MatchDate.PATTERN) String> dates,
		@ArraySchema(arraySchema = @Schema(description = "선택한 자치구 코드 목록. 1개 이상 최대 3개.",
				requiredMode = Schema.RequiredMode.REQUIRED),
				schema = @Schema(type = "string", example = "11440"), minItems = 1, maxItems = 3)
		@NotEmpty @Size(max = 3) List<@NotNull @Pattern(regexp = "[0-9]{5}") String> areaCodes,
		@Schema(description = "각 날짜의 가장 이른 경기 시작 시각(HH:mm). 생략하면 00:00부터 조회합니다.", example = "14:00")
		@Pattern(regexp = CLOCK_TIME) String startTimeFrom,
		@Schema(description = "각 날짜의 가장 늦은 경기 종료 시각(HH:mm). 24:00 또는 생략 시 다음 날 자정까지 조회합니다.",
				example = "24:00")
		@Pattern(regexp = CLOCK_TIME + "|24:00") String startTimeTo,
		@Schema(description = "선택한 경기 방식 중 하나 이상과 일치해야 합니다. DOUBLES는 남복·여복을 포함합니다.")
		List<@NotNull PreferredMatchType> matchTypes,
		@Schema(description = "희망 NTRP 최솟값. maxNtrp와 함께 전달합니다. 모집 범위와 경계를 포함해 겹치면 반환하며, "
				+ "설정되지 않은 모집 경계에는 제한이 없습니다.", example = "2.5",
				minimum = "1.0", maximum = "4.0", multipleOf = 0.5)
		BigDecimal minNtrp,
		@Schema(description = "희망 NTRP 최댓값. minNtrp와 함께 전달합니다. 4.0+는 숫자 4.0으로 전달합니다.",
				example = "4.0", minimum = "1.0", maximum = "4.0", multipleOf = 0.5)
		BigDecimal maxNtrp,
		@Schema(description = "같은 성별의 호스트가 동성만으로 개설한 매치만 조회합니다.")
		Boolean sameGenderOnly) {

	private static final String CLOCK_TIME = "(?:[01][0-9]|2[0-3]):[0-5][0-9]";
}
