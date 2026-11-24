package com.rallit.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.rallit.domain.match.dto.request.MatchCreateRequest.Recruitment;
import com.rallit.domain.match.dto.request.MatchCreateRequest.Reservation;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.user.enums.Gender;
import com.rallit.global.apiPayload.code.BaseErrorCode;
import com.rallit.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatchCreationRulesTest {

	private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

	@Test
	void derivesTheRecruitmentDeadlineOneHourBeforeStartAndSupportsMidnightEnd() {
		var schedule = MatchCreationRules.schedule(reservation("2026-11-21", "22:00", "24:00"), NOW);

		assertThat(schedule.startsAt()).isEqualTo(Instant.parse("2026-11-21T13:00:00Z"));
		assertThat(schedule.endsAt()).isEqualTo(Instant.parse("2026-11-21T15:00:00Z"));
		assertThat(schedule.recruitmentDeadline()).isEqualTo(Instant.parse("2026-11-21T12:00:00Z"));
	}

	@ParameterizedTest
	@CsvSource({"16:00, 14:00", "14:00, 14:00"})
	void rejectsReservationsThatDoNotEndAfterTheyStart(String start, String end) {
		assertFails(() -> MatchCreationRules.schedule(reservation("2026-11-21", start, end), NOW),
				MatchErrorStatus.MATCH_INVALID_SCHEDULE);
	}

	@ParameterizedTest
	@CsvSource({"2026-11-19, 14:00", "2026-11-20, 11:00", "2026-11-20, 13:00"})
	void rejectsPastStartedOrTooSoonReservations(String date, String start) {
		assertFails(() -> MatchCreationRules.schedule(reservation(date, start, "23:00"), NOW),
				MatchErrorStatus.MATCH_START_TOO_SOON);
	}

	@Test
	void acceptsAReservationThatStillLeavesRecruitmentTime() {
		assertThatCode(() -> MatchCreationRules.schedule(reservation("2026-11-20", "13:01", "15:00"), NOW))
				.doesNotThrowAnyException();
	}

	@Test
	void acceptsReservationsUpToFourteenDaysAheadInSeoul() {
		assertThatCode(() -> MatchCreationRules.schedule(reservation("2026-12-04", "23:00", "24:00"), NOW))
				.doesNotThrowAnyException();
		assertFails(() -> MatchCreationRules.schedule(reservation("2026-12-05", "06:00", "08:00"), NOW),
				MatchErrorStatus.MATCH_RESERVATION_DATE_OUT_OF_RANGE);
	}

	@ParameterizedTest
	@CsvSource({"0, 0", "3, 3", "5, 1"})
	void limitsTheTotalCapacityIncludingTheHost(int male, int female) {
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.RALLY), false, male, female), Gender.MALE),
				MatchErrorStatus.MATCH_INVALID_RECRUIT_COUNT);
	}

	@Test
	void sameGenderOnlyRecruitsOnlyTheHostsGenderAndExcludesMixedDoubles() {
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.RALLY), true, 1, 1), Gender.MALE),
				MatchErrorStatus.MATCH_SAME_GENDER_CONFLICT);
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.MIXED_DOUBLES), true, 0, 3), Gender.FEMALE),
				MatchErrorStatus.MATCH_SAME_GENDER_CONFLICT);
		assertThatCode(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.WOMENS_DOUBLES), true, 0, 3), Gender.FEMALE))
				.doesNotThrowAnyException();
	}

	@Test
	void requiresAGenderCompositionThatMatchesTheMatchType() {
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.MENS_DOUBLES), false, 0, 3), Gender.FEMALE),
				MatchErrorStatus.MATCH_TYPE_GENDER_CONFLICT);
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.WOMENS_DOUBLES), false, 1, 2), Gender.FEMALE),
				MatchErrorStatus.MATCH_TYPE_GENDER_CONFLICT);
		assertFails(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.MIXED_DOUBLES), false, 3, 0), Gender.MALE),
				MatchErrorStatus.MATCH_TYPE_GENDER_CONFLICT);
		assertThatCode(() -> MatchCreationRules.validateRecruitment(
				recruitment(Set.of(MatchType.MIXED_DOUBLES), false, 1, 2), Gender.MALE))
				.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource({"4.5,", ",0.5", "3.3,", "3.5, 3.0"})
	void reusesTheRecruitmentNtrpPolicy(BigDecimal min, BigDecimal max) {
		var recruitment = new Recruitment(Set.of(MatchType.SINGLES), false, 1, 0, min, max, Set.of(), 0);

		assertFails(() -> MatchCreationRules.validateRecruitment(recruitment, Gender.MALE),
				MatchErrorStatus.MATCH_INVALID_RECRUITMENT_NTRP);
	}

	@Test
	void allowsOpenOrUpperBandNtrpBounds() {
		var recruitment = new Recruitment(Set.of(MatchType.SINGLES), false, 1, 0, new BigDecimal("4.0"),
				new BigDecimal("4.0"), null, 0);

		assertThatCode(() -> MatchCreationRules.validateRecruitment(recruitment, Gender.MALE))
				.doesNotThrowAnyException();
	}

	private static Reservation reservation(String date, String start, String end) {
		return new Reservation(1L, "3", date, start, end, null);
	}

	private static Recruitment recruitment(Set<MatchType> types, boolean sameGenderOnly, int male, int female) {
		return new Recruitment(types, sameGenderOnly, male, female, null, null, Set.of(), 10000);
	}

	private static void assertFails(Runnable rule, BaseErrorCode expected) {
		assertThatThrownBy(rule::run).isInstanceOfSatisfying(GeneralException.class,
				failure -> assertThat(failure.getCode()).isEqualTo(expected));
	}
}
