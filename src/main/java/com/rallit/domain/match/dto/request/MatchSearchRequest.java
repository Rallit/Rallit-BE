package com.rallit.domain.match.dto.request;

import java.time.LocalTime;
import java.util.List;

import com.rallit.domain.user.enums.PreferredMatchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public record MatchSearchRequest(
		@Schema(description = "경기 시작일(서울 기준). YYYY-MM-DD, 연도 0001~9999.",
				type = "string", format = "date", example = "2026-11-21")
		@NotNull @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String date,
		@NotEmpty @Size(max = 25) List<@NotNull @Pattern(regexp = "[0-9]{5}") String> areaCodes,
		@Schema(description = "가장 이른 경기 시작 시각. 생략하면 자정부터 조회합니다.", example = "14:00")
		@DateTimeFormat(pattern = "HH:mm") LocalTime startTimeFrom,
		@Schema(description = "가장 늦은 경기 종료 시각. 생략하면 다음 날 자정까지 조회합니다.", example = "17:00")
		@DateTimeFormat(pattern = "HH:mm") LocalTime startTimeTo,
		@Schema(description = "선택한 경기 방식 중 하나 이상과 일치해야 합니다. DOUBLES는 남복·여복을 포함합니다.")
		List<@NotNull PreferredMatchType> matchTypes,
		@Schema(description = "선택값 모두가 모집 범위에 포함되어야 합니다. 4.0+는 URL에서 4.0%2B로 전송합니다.")
		List<@NotNull @Pattern(regexp = "(?:[1-3]\\.[05]|4\\.0\\+)") String> ntrp,
		@Schema(description = "같은 성별의 호스트가 동성만으로 개설한 매치만 조회합니다.")
		Boolean sameGenderOnly) {
}
