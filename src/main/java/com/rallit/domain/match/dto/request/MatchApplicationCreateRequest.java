package com.rallit.domain.match.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record MatchApplicationCreateRequest(
		@NotNull(message = "환불·취소 규정 동의 여부는 필수입니다.")
		@AssertTrue(message = "환불·취소 규정에 동의해야 신청할 수 있습니다.")
		Boolean refundPolicyAgreed) {
}
