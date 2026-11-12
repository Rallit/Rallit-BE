package com.rallit.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.region.entity.Area;
import com.rallit.domain.user.entity.OnboardingProfile;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.enums.PreferredTimeSlot;
import jakarta.persistence.EntityManager;

public final class EntityFixtures {

	public static final LocalDateTime MATCH_STARTS_AT = LocalDateTime.of(2026, 11, 14, 14, 0);

	private EntityFixtures() {
	}

	public static User user(EntityManager entityManager, String nickname, Gender gender) {
		Area area = entityManager.createQuery("select a from Area a where a.code = '11440'", Area.class)
				.getSingleResult();
		User user = User.signUp();
		user.completeOnboarding(new OnboardingProfile(nickname, gender, AgeGroup.TWENTIES,
				ExperiencePeriod.ONE_TO_THREE_YEARS, new BigDecimal("3.0"), false,
				Set.of(PreferredMatchType.SINGLES), Set.of(area), Set.of(PreferredTimeSlot.WEEKEND_MORNING)));
		return user;
	}

	public static Court court() {
		return Court.builder()
				.name("장충테니스장")
				.address("서울특별시 중구 동호로 241")
				.latitude(new BigDecimal("37.558651"))
				.longitude(new BigDecimal("127.004377"))
				.hasParking(true)
				.hasShower(true)
				.build();
	}

	public static CourtSlot courtSlot(Court court, User registrant, String courtNumber, LocalDateTime startsAt) {
		return CourtSlot.builder()
				.court(court)
				.registrant(registrant)
				.courtNumber(courtNumber)
				.startsAt(startsAt)
				.endsAt(startsAt.plusHours(2))
				.rentalFee(14000)
				.evidenceFileKey("court-slot/evidence.png")
				.build();
	}

	public static Match match(CourtSlot courtSlot, User host) {
		return Match.builder()
				.courtSlot(courtSlot)
				.host(host)
				.matchType(MatchType.MIXED_DOUBLES)
				.maleRecruitCount(1)
				.femaleRecruitCount(2)
				.minNtrp(new BigDecimal("2.5"))
				.maxNtrp(new BigDecimal("3.5"))
				.participationFee(3500)
				.recruitmentDeadline(MATCH_STARTS_AT.minusDays(1))
				.preferredAgeGroups(Set.of(AgeGroup.TWENTIES, AgeGroup.THIRTIES))
				.build();
	}

	public static MatchApplication matchApplication(Match match, User applicant) {
		return MatchApplication.create(match, applicant, MATCH_STARTS_AT.minusDays(3));
	}
}
