package com.rallit.domain.match.dto.request;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.enums.AgeGroup;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MatchCreateRequest(
		@Schema(description = "코트 예약 정보(04-A)", requiredMode = Schema.RequiredMode.REQUIRED)
		@Valid @NotNull Reservation reservation,
		@Schema(description = "매치 모집 조건(04-B)", requiredMode = Schema.RequiredMode.REQUIRED)
		@Valid @NotNull Recruitment recruitment) {

	private static final String CLOCK_TIME = "(?:[01][0-9]|2[0-3]):[0-5][0-9]";
	private static final int COURT_NUMBER_MAX_LENGTH = 20;

	public record Reservation(
			@Schema(description = "코트 등록 API에서 받은 코트 ID", example = "12")
			@NotNull @Positive Long courtId,
			@Schema(description = "코트 번호. 앞뒤 공백을 제외하고 저장하며 최대 20자", example = "3")
			@NotBlank @Size(max = COURT_NUMBER_MAX_LENGTH) String courtNumber,
			@Schema(description = "예약 날짜(서울 기준). 오늘부터 14일 뒤까지 선택할 수 있습니다.",
					type = "string", format = "date", example = "2026-11-21")
			@NotNull @Pattern(regexp = MatchDate.PATTERN) String date,
			@Schema(description = "이용 시작 시각(HH:mm)", example = "14:00")
			@NotNull @Pattern(regexp = CLOCK_TIME) String startTime,
			@Schema(description = "이용 종료 시각(HH:mm). 24:00은 다음 날 자정입니다.", example = "16:00")
			@NotNull @Pattern(regexp = CLOCK_TIME + "|24:00") String endTime,
			@Schema(description = "예약 증빙 업로드 ID(최대 1장). 증빙 없이 개설하면 생략합니다.",
					types = {"string", "null"}, format = "uuid")
			UUID evidenceUploadId) {
	}

	public record Recruitment(
			@ArraySchema(arraySchema = @Schema(description = "경기 방식. 1개 이상", requiredMode = Schema.RequiredMode.REQUIRED),
					minItems = 1)
			@NotEmpty Set<@NotNull MatchType> matchTypes,
			@Schema(description = "true이면 호스트와 같은 성별만 모집합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull Boolean sameGenderOnly,
			@Schema(description = "호스트를 제외한 남성 모집 인원", example = "1", maximum = "5")
			@NotNull @PositiveOrZero @Max(5) Integer maleRecruitCount,
			@Schema(description = "호스트를 제외한 여성 모집 인원", example = "2", maximum = "5")
			@NotNull @PositiveOrZero @Max(5) Integer femaleRecruitCount,
			@Schema(description = "권장 NTRP 최솟값. 1.0~4.0을 0.5 단위로 입력하며, 생략하면 하한이 없습니다.",
					example = "2.5", types = {"number", "null"})
			BigDecimal minNtrp,
			@Schema(description = "권장 NTRP 최댓값. 4.0+는 4.0으로 입력하며, 생략하면 상한이 없습니다.",
					example = "4.0", types = {"number", "null"})
			BigDecimal maxNtrp,
			@ArraySchema(arraySchema = @Schema(description = "선호 나이대. 비우거나 생략하면 나이대 상관없음입니다."))
			Set<@NotNull AgeGroup> preferredAgeGroups,
			@Schema(description = "참가자 1인당 참가비(원). 코트 이용료와 별개로 입력합니다.", example = "10000")
			@NotNull @PositiveOrZero Integer participationFee) {
	}
}
