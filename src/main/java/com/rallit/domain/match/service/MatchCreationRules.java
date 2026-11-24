package com.rallit.domain.match.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.rallit.domain.match.dto.request.MatchCreateRequest.Recruitment;
import com.rallit.domain.match.dto.request.MatchCreateRequest.Reservation;
import com.rallit.domain.match.dto.request.MatchDate;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.user.enums.Gender;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.config.TimeConfig;

final class MatchCreationRules {

	private static final String MIDNIGHT = "24:00";
	private static final int MIN_CAPACITY = 2;
	private static final int MAX_CAPACITY = 6;
	private static final int RESERVABLE_DAYS_AHEAD = 14;

	private MatchCreationRules() {
	}

	static Schedule schedule(Reservation reservation, Instant now) {
		var date = MatchDate.parse(reservation.date());
		if (date.isAfter(LocalDate.ofInstant(now, TimeConfig.SERVICE_ZONE).plusDays(RESERVABLE_DAYS_AHEAD))) {
			throw new GeneralException(MatchErrorStatus.MATCH_RESERVATION_DATE_OUT_OF_RANGE);
		}
		var startsAt = date.atTime(LocalTime.parse(reservation.startTime())).atZone(TimeConfig.SERVICE_ZONE).toInstant();
		var endsAt = MIDNIGHT.equals(reservation.endTime())
				? date.plusDays(1).atStartOfDay(TimeConfig.SERVICE_ZONE).toInstant()
				: date.atTime(LocalTime.parse(reservation.endTime())).atZone(TimeConfig.SERVICE_ZONE).toInstant();
		if (!startsAt.isBefore(endsAt)) {
			throw new GeneralException(MatchErrorStatus.MATCH_INVALID_SCHEDULE);
		}
		var recruitmentDeadline = MatchApplicationEligibility.finalDeadline(startsAt);
		if (!now.isBefore(recruitmentDeadline)) {
			throw new GeneralException(MatchErrorStatus.MATCH_START_TOO_SOON);
		}
		return new Schedule(startsAt, endsAt, recruitmentDeadline);
	}

	static void validateRecruitment(Recruitment recruitment, Gender hostGender) {
		int male = recruitment.maleRecruitCount();
		int female = recruitment.femaleRecruitCount();
		int capacity = 1 + male + female;
		if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
			throw new GeneralException(MatchErrorStatus.MATCH_INVALID_RECRUIT_COUNT);
		}
		var types = recruitment.matchTypes();
		int oppositeGenderCount = hostGender == Gender.MALE ? female : male;
		if (recruitment.sameGenderOnly() && (oppositeGenderCount > 0 || types.contains(MatchType.MIXED_DOUBLES))) {
			throw new GeneralException(MatchErrorStatus.MATCH_SAME_GENDER_CONFLICT);
		}
		if ((types.contains(MatchType.MENS_DOUBLES) && (hostGender != Gender.MALE || female > 0))
				|| (types.contains(MatchType.WOMENS_DOUBLES) && (hostGender != Gender.FEMALE || male > 0))
				|| (types.contains(MatchType.MIXED_DOUBLES) && oppositeGenderCount == 0)) {
			throw new GeneralException(MatchErrorStatus.MATCH_TYPE_GENDER_CONFLICT);
		}
		Match.validateNtrpRange(recruitment.minNtrp(), recruitment.maxNtrp());
	}

	record Schedule(Instant startsAt, Instant endsAt, Instant recruitmentDeadline) {
	}
}
