package com.rallit.global.apiPayload;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, boolean hasNext,
		@Schema(description = "필터 적용 후 페이지를 나누기 전의 전체 건수", requiredMode = Schema.RequiredMode.REQUIRED)
		long totalElements) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.hasNext(),
				page.getTotalElements());
	}
}
