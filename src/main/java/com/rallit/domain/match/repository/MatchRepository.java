package com.rallit.domain.match.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.enums.Gender;
import com.rallit.global.config.TimeConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

	String LOCAL_STARTS_AT = "cast(function('timezone', '" + TimeConfig.SERVICE_ZONE_ID
			+ "', s.startsAt) as LocalDateTime)";

	String LOCAL_ENDS_AT = "cast(function('timezone', '" + TimeConfig.SERVICE_ZONE_ID
			+ "', s.endsAt) as LocalDateTime)";

	String LOCAL_START_DATE = "cast(" + LOCAL_STARTS_AT + " as LocalDate)";

	String LOCAL_START_HOUR = "extract(hour from " + LOCAL_STARTS_AT + ")";

	// Keep the final deadline aligned with MatchApplicationEligibility.finalDeadline.
	String RECRUITING_FILTER = """
			where m.status = :matchStatus
			and s.startsAt > timestampadd(hour, 1, :now) and m.recruitmentDeadline > :now
			""";

	String MATCH_TYPE_EXISTS = """
			exists (
				select t from Match candidate join candidate.matchTypes t
				where candidate.id = m.id and t in :types
			)
			""";

	String MATCH_TYPE_FILTER = "and (:allTypes = true or " + MATCH_TYPE_EXISTS + ") ";

	String DISCOVERY_FILTER = RECRUITING_FILTER + """
			and (cast(:from as Instant) is null or s.startsAt >= :from)
			and (cast(:before as Instant) is null or s.startsAt < :before)
			and (cast(:endsBy as Instant) is null or s.endsAt <= :endsBy)
			""" + MATCH_TYPE_FILTER;

	// Keep the eligibility predicates aligned with MatchApplicationEligibility.
	String APPLICANT_FILTER = """
			and m.host.id <> :applicantId
			and (m.sameGenderOnly = false or m.host.gender = :gender)
			and (select count(a) from MatchApplication a
				where a.match.id = m.id and a.applicantGender = :gender
				and a.status in (com.rallit.domain.match.enums.MatchApplicationStatus.PAYMENT_PENDING,
					com.rallit.domain.match.enums.MatchApplicationStatus.CONFIRMED))
				< (case when :gender = com.rallit.domain.user.enums.Gender.MALE
					then m.maleRecruitCount else m.femaleRecruitCount end)
			and not exists (
				select a.id from MatchApplication a
				where a.match.id = m.id and a.applicant.id = :applicantId and a.status in :activeStatuses
			)
			""";

	String SAME_GENDER_PREFERENCE_FILTER = """
			and (:sameGenderOnly = false or m.sameGenderOnly = true)
			""";

	String HAS_NTRP_BOUND = """
			(m.minNtrp is not null or m.maxNtrp is not null)
			""";

	String HOME_RECOMMENDATION_FILTER = RECRUITING_FILTER + MATCH_TYPE_FILTER + APPLICANT_FILTER
			+ SAME_GENDER_PREFERENCE_FILTER + """
			and area.code in :areaCodes
			""" + "and (case when extract(day of week from " + LOCAL_STARTS_AT + ") in (1, 7)"
			+ " then case when " + LOCAL_START_HOUR + " < 12 then 'WEEKEND_MORNING' else 'WEEKEND_AFTERNOON' end"
			+ " else case when " + LOCAL_START_HOUR + " < 12 then 'WEEKDAY_MORNING' else 'WEEKDAY_AFTERNOON' end"
			+ " end) in :timeSlots ";

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			""" + DISCOVERY_FILTER + "order by s.startsAt, m.id",
			countQuery = "select count(m) from Match m join m.courtSlot s " + DISCOVERY_FILTER)
	Page<Match> findRecruitingMatches(MatchStatus matchStatus, Instant now,
			Instant from, Instant before, Instant endsBy, boolean allTypes,
			Collection<MatchType> types, Pageable pageable);

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			""" + DISCOVERY_FILTER + APPLICANT_FILTER + "order by s.startsAt, m.id",
			countQuery = "select count(m) from Match m join m.courtSlot s " + DISCOVERY_FILTER + APPLICANT_FILTER)
	Page<Match> findApplicableMatches(MatchStatus matchStatus, Instant now,
			Instant from, Instant before, Instant endsBy, boolean allTypes,
			Collection<MatchType> types, Long applicantId, Gender gender,
			Collection<MatchApplicationStatus> activeStatuses, Pageable pageable);

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court c
			join fetch c.area area
			""" + HOME_RECOMMENDATION_FILTER + "order by case when " + HAS_NTRP_BOUND + """
				and (m.minNtrp is null or m.minNtrp <= :ntrp)
				and (m.maxNtrp is null or m.maxNtrp >= :ntrp)
				then 0 else 1 end, s.startsAt, m.id
			""", countQuery = """
			select count(m) from Match m join m.courtSlot s join s.court c join c.area area
			""" + HOME_RECOMMENDATION_FILTER)
	Page<Match> findHomeRecommendations(MatchStatus matchStatus, Instant now,
			boolean allTypes, Collection<MatchType> types, Long applicantId, Gender gender,
			Collection<MatchApplicationStatus> activeStatuses, boolean sameGenderOnly, Collection<String> areaCodes,
			Collection<String> timeSlots, BigDecimal ntrp, Pageable pageable);

	@Query("""
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court c
			join fetch c.area
			""" + RECRUITING_FILTER + "and " + MATCH_TYPE_EXISTS + APPLICANT_FILTER + SAME_GENDER_PREFERENCE_FILTER + """
			and s.startsAt >= :from and s.startsAt < :before
			""" + "and " + LOCAL_START_DATE + " in :dates "
			+ "and cast(" + LOCAL_STARTS_AT + " as LocalTime) >= :startTime "
			+ "and " + LOCAL_ENDS_AT + " <= timestampadd(minute, :endMinute, cast(" + LOCAL_START_DATE
			+ " as LocalDateTime)) " + """
			and (m.minNtrp is null or m.minNtrp <= :maximumNtrp)
			and (m.maxNtrp is null or m.maxNtrp >= :minimumNtrp)
			order by s.startsAt, m.id
			""")
	List<Match> findSearchCandidates(MatchStatus matchStatus, Instant now,
			Instant from, Instant before, Collection<LocalDate> dates, LocalTime startTime, long endMinute,
			Collection<MatchType> types, Long applicantId, Gender gender,
			Collection<MatchApplicationStatus> activeStatuses, boolean sameGenderOnly,
			BigDecimal minimumNtrp, BigDecimal maximumNtrp);

	@Query("""
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			join fetch m.host
			where m.id = :id and m.status = :matchStatus and s.startsAt > :now
			""")
	Optional<Match> findDiscoveryDetail(Long id, MatchStatus matchStatus, Instant now);

	@Query("select m.host.id from Match m where m.id = :id")
	Optional<Long> findHostIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Match m where m.id = :id")
	Optional<Match> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select m from Match m
			join fetch m.host
			left join fetch m.matchTypes
			join fetch m.courtSlot s
			join fetch s.court
			where m.id = :id
			""")
	Optional<Match> findDetailById(@Param("id") Long id);
}
