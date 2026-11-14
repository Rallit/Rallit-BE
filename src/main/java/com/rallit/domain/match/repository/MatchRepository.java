package com.rallit.domain.match.repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

	String DISCOVERY_FILTER = """
			where s.status = :slotStatus and m.status = :matchStatus
			and s.startsAt > :now and m.recruitmentDeadline > :now
			and (cast(:from as LocalDateTime) is null or s.startsAt >= :from)
			and (cast(:before as LocalDateTime) is null or s.startsAt < :before)
			and (cast(:endsBy as LocalDateTime) is null or s.endsAt <= :endsBy)
			and (:allTypes = true or exists (
				select t from Match candidate join candidate.matchTypes t
				where candidate.id = m.id and t in :types
			))
			""";

	// Keep the eligibility predicates aligned with MatchApplicationEligibility.
	String APPLICANT_FILTER = """
			and m.host.id <> :applicantId
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

	@Query("""
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			join fetch m.host
			where m.id = :id and s.status = :slotStatus and m.status = :matchStatus and s.startsAt > :now
			""")
	Optional<Match> findDiscoveryDetail(Long id, CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now);
}
