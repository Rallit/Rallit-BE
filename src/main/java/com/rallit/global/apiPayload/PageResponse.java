package com.rallit.global.apiPayload;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, boolean hasNext, long totalElements) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.hasNext(),
				page.getTotalElements());
	}
}
