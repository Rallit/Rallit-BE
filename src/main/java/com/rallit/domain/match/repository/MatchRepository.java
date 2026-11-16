package com.rallit.domain.match.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.enums.Gender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<Match, Long> {

	String RECRUITING_FILTER = """
			where s.status = :slotStatus and m.status = :matchStatus
			and s.startsAt > :now and m.recruitmentDeadline > :now
			""";

	String MATCH_TYPE_EXISTS = """
			exists (
				select t from Match candidate join candidate.matchTypes t
				where candidate.id = m.id and t in :types
			)
			""";

	String MATCH_TYPE_FILTER = "and (:allTypes = true or " + MATCH_TYPE_EXISTS + ") ";

	String DISCOVERY_FILTER = RECRUITING_FILTER + """
			and (cast(:from as LocalDateTime) is null or s.startsAt >= :from)
			and (cast(:before as LocalDateTime) is null or s.startsAt < :before)
			and (cast(:endsBy as LocalDateTime) is null or s.endsAt <= :endsBy)
			""" + MATCH_TYPE_FILTER;

	// Keep the eligibility predicates aligned with MatchApplicationEligibility.
	String APPLICANT_FILTER = """
			and m.host.id <> :applicantId
			and (m.sameGenderOnly = false or m.host.gender = :gender)
			and (select count(a) from MatchApplication a
				where a.match.id = m.id and a.applicantGender = :gender
				and a.status = com.rallit.domain.match.enums.MatchApplicationStatus.CONFIRMED)
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
			and (case
				when extract(day of week from s.startsAt) in (1, 7) then
					case when extract(hour from s.startsAt) < 12 then 'WEEKEND_MORNING' else 'WEEKEND_AFTERNOON' end
				else case when extract(hour from s.startsAt) < 12 then 'WEEKDAY_MORNING' else 'WEEKDAY_AFTERNOON' end
			end) in :timeSlots
			""";

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			""" + DISCOVERY_FILTER + "order by s.startsAt, m.id",
			countQuery = "select count(m) from Match m join m.courtSlot s " + DISCOVERY_FILTER)
	Page<Match> findRecruitingMatches(CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now,
			LocalDateTime from, LocalDateTime before, LocalDateTime endsBy, boolean allTypes,
			Collection<MatchType> types, Pageable pageable);

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			""" + DISCOVERY_FILTER + APPLICANT_FILTER + "order by s.startsAt, m.id",
			countQuery = "select count(m) from Match m join m.courtSlot s " + DISCOVERY_FILTER + APPLICANT_FILTER)
	Page<Match> findApplicableMatches(CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now,
			LocalDateTime from, LocalDateTime before, LocalDateTime endsBy, boolean allTypes,
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
	Page<Match> findHomeRecommendations(CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now,
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
			and cast(s.startsAt as LocalDate) in :dates
			and cast(s.startsAt as LocalTime) >= :startTime
			and s.endsAt <= timestampadd(minute, :endMinute, cast(cast(s.startsAt as LocalDate) as LocalDateTime))
			and (m.minNtrp is null or m.minNtrp <= :maximumNtrp)
			and (m.maxNtrp is null or m.maxNtrp >= :minimumNtrp)
			order by s.startsAt, m.id
			""")
	List<Match> findSearchCandidates(CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now,
			LocalDateTime from, LocalDateTime before, Collection<LocalDate> dates, LocalTime startTime, long endMinute,
			Collection<MatchType> types, Long applicantId, Gender gender,
			Collection<MatchApplicationStatus> activeStatuses, boolean sameGenderOnly,
			BigDecimal minimumNtrp, BigDecimal maximumNtrp);

	@Query("""
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			join fetch m.host
			where m.id = :id and s.status = :slotStatus and m.status = :matchStatus and s.startsAt > :now
			""")
	Optional<Match> findDiscoveryDetail(Long id, CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now);
}
