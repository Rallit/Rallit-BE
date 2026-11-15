package com.rallit.domain.match.exception;

public class InvalidMatchNtrpException extends IllegalArgumentException {

	public InvalidMatchNtrpException() {
		super("모집 NTRP는 1.0부터 4.0+까지 0.5 단위로 선택해야 합니다.");
	}
}
