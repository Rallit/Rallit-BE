package com.rallit.domain.match.dto.request;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;

public final class MatchDate {

	public static final String PATTERN = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

	private MatchDate() {
	}

	public static LocalDate parse(String value) {
		try {
			var date = LocalDate.parse(value);
			if (date.getYear() >= 1 && date.getYear() <= 9999) {
				return date;
			}
		} catch (DateTimeParseException e) {
			throw new GeneralException(ErrorStatus._BAD_REQUEST, e);
		}
		throw new GeneralException(ErrorStatus._BAD_REQUEST);
	}
}
