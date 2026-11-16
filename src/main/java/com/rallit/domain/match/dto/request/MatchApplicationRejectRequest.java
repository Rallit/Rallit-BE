package com.rallit.domain.match.dto.request;

import com.rallit.domain.match.enums.MatchApplicationRejectReason;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchApplicationRejectRequest(
		@Schema(description = "거절 사유(선택). SKILL_GAP 실력 차이가 커요, COMPOSITION_MISMATCH 인원 구성이 맞지 않아요, "
				+ "ALREADY_HAVE_PARTNER 이미 함께할 사람이 있어요, OTHER 기타", types = {"string", "null"},
				example = "SKILL_GAP")
		MatchApplicationRejectReason reason) {
}
