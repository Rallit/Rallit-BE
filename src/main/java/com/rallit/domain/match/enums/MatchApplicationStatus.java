package com.rallit.domain.match.enums;

import java.util.Set;

public enum MatchApplicationStatus {
	PENDING,
	PAYMENT_PENDING,
	CONFIRMED,
	REJECTED,
	CANCELED,
	EXPIRED;

	public static final Set<MatchApplicationStatus> ACTIVE = Set.of(PENDING, PAYMENT_PENDING, CONFIRMED);

	public static final Set<MatchApplicationStatus> CLOSED = Set.of(REJECTED, CANCELED, EXPIRED);

	public static final Set<MatchApplicationStatus> SEAT_OCCUPYING = Set.of(PAYMENT_PENDING, CONFIRMED);
}
