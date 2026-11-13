package com.rallit.domain.match.repository;

import static com.rallit.support.EntityFixtures.MATCH_STARTS_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.court.repository.CourtSlotRepository;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.support.EntityFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("test")
@Transactional
class MatchRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourtRepository courtRepository;

	@Autowired
	private CourtSlotRepository courtSlotRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private MatchApplicationRepository matchApplicationRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User host;

	private Court court;

	@BeforeEach
	void setUp() {
		host = userRepository.save(EntityFixtures.user("호스트", Gender.MALE));
		court = courtRepository.save(EntityFixtures.court());
	}

	@Test
	void savesMatchWithCourtSlotAndPreferredAgeGroups() {
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		Match match = matchRepository.saveAndFlush(EntityFixtures.match(courtSlot, host));
		entityManager.clear();

		Match found = matchRepository.findById(match.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(MatchStatus.PENDING_APPROVAL);
		assertThat(found.getMatchTypes()).containsExactly(MatchType.MIXED_DOUBLES);
		assertThat(found.getCourtSlot().getStatus()).isEqualTo(CourtSlotStatus.PENDING);
		assertThat(found.getHost().getId()).isEqualTo(host.getId());
		assertThat(found.getPreferredAgeGroups()).containsExactlyInAnyOrder(AgeGroup.TWENTIES, AgeGroup.THIRTIES);
		assertThat(jdbcTemplate.queryForObject("SELECT match_type FROM match_match_type WHERE match_id = ?", String.class,
				match.getId())).isEqualTo("MIXED_DOUBLES");
	}

	@Test
	void rejectsOverlappingCourtSlotOnSameCourtNumber() {
		courtSlotRepository.saveAndFlush(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		courtSlotRepository.saveAndFlush(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT.plusHours(2)));
		courtSlotRepository.saveAndFlush(EntityFixtures.courtSlot(court, host, "4", MATCH_STARTS_AT));

		assertThatThrownBy(() -> courtSlotRepository.saveAndFlush(
				EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT.plusHours(1))))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ex_court_slot_overlap");
	}

	@Test
	void allowsOverlappingCourtSlotAfterRejection() {
		CourtSlot rejected = courtSlotRepository.saveAndFlush(
				EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		jdbcTemplate.update("UPDATE court_slot SET status = 'REJECTED' WHERE id = ?", rejected.getId());

		courtSlotRepository.saveAndFlush(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));

		assertThat(courtSlotRepository.count()).isEqualTo(2);
	}

	@Test
	void rejectsSecondMatchOnSameCourtSlot() {
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		matchRepository.saveAndFlush(EntityFixtures.match(courtSlot, host));

		assertThatThrownBy(() -> matchRepository.saveAndFlush(EntityFixtures.match(courtSlot, host)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_match_court_slot_id");
	}

	@Test
	void rejectsTotalCapacityOverSixIncludingHost() {
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		Match match = Match.builder()
				.courtSlot(courtSlot)
				.host(host)
				.matchTypes(Set.of(MatchType.RALLY))
				.maleRecruitCount(3)
				.femaleRecruitCount(3)
				.participationFee(3000)
				.recruitmentDeadline(MATCH_STARTS_AT.minusDays(1))
				.build();

		assertThatThrownBy(() -> matchRepository.saveAndFlush(match))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_match_recruit_count");
	}

	@Test
	void rejectsMatchWithoutRecruits() {
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		Match match = Match.builder()
				.courtSlot(courtSlot)
				.host(host)
				.matchTypes(Set.of(MatchType.SINGLES))
				.maleRecruitCount(0)
				.femaleRecruitCount(0)
				.participationFee(7000)
				.recruitmentDeadline(MATCH_STARTS_AT.minusDays(1))
				.build();

		assertThatThrownBy(() -> matchRepository.saveAndFlush(match))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_match_recruit_count");
	}

	@Test
	void savesApplicationWithApplicantGenderSnapshot() {
		Match match = savedMatch();
		User applicant = userRepository.save(EntityFixtures.user("신청자", Gender.FEMALE));
		MatchApplication application = matchApplicationRepository.saveAndFlush(
				EntityFixtures.matchApplication(match, applicant));
		entityManager.clear();

		MatchApplication found = matchApplicationRepository.findById(application.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(MatchApplicationStatus.PENDING);
		assertThat(found.getApplicantGender()).isEqualTo(Gender.FEMALE);
	}

	@Test
	void rejectsApplicationFromOnboardingApplicant() {
		Match match = savedMatch();
		User applicant = userRepository.save(User.signUp(null));

		assertThatThrownBy(() -> matchApplicationRepository.saveAndFlush(
				EntityFixtures.matchApplication(match, applicant)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("applicant_gender");
	}

	@Test
	void rejectsDuplicateActiveApplication() {
		Match match = savedMatch();
		User applicant = userRepository.save(EntityFixtures.user("중복신청", Gender.FEMALE));
		matchApplicationRepository.saveAndFlush(EntityFixtures.matchApplication(match, applicant));

		assertThatThrownBy(() -> matchApplicationRepository.saveAndFlush(
				EntityFixtures.matchApplication(match, applicant)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_match_application_active");
	}

	@Test
	void allowsReapplicationAfterCancellation() {
		Match match = savedMatch();
		User applicant = userRepository.save(EntityFixtures.user("재신청", Gender.FEMALE));
		MatchApplication canceled = matchApplicationRepository.saveAndFlush(
				EntityFixtures.matchApplication(match, applicant));
		jdbcTemplate.update("UPDATE match_application SET status = 'CANCELED' WHERE id = ?", canceled.getId());

		matchApplicationRepository.saveAndFlush(EntityFixtures.matchApplication(match, applicant));

		assertThat(matchApplicationRepository.count()).isEqualTo(2);
	}

	private Match savedMatch() {
		CourtSlot courtSlot = courtSlotRepository.save(EntityFixtures.courtSlot(court, host, "3", MATCH_STARTS_AT));
		return matchRepository.save(EntityFixtures.match(courtSlot, host));
	}
}
