package com.rallit.domain.match.service;

import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import org.springframework.data.domain.Page;

final class MatchPageRange {

	private MatchPageRange() {
	}

	static void requireInRange(Page<?> page) {
		if (page.getNumber() > 0 && page.getNumber() >= page.getTotalPages()) {
			throw new GeneralException(MatchErrorStatus.MATCH_PAGE_OUT_OF_RANGE);
		}
	}
}
