package com.rallit.domain.court.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvidenceRejectRequest(
		@Schema(description = "호스트에게 보여 줄 반려 사유", example = "예약자명과 날짜가 보이도록 다시 캡처해 주세요.")
		@NotBlank @Size(max = 500) String reason) {
}
