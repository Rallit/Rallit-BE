package com.rallit.domain.match.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import com.rallit.domain.court.enums.CourtSlotStatus;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
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

	@Query(value = """
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			""" + DISCOVERY_FILTER + "order by s.startsAt, m.id",
			countQuery = "select count(m) from Match m join m.courtSlot s " + DISCOVERY_FILTER)
	Page<Match> findRecruitingMatches(CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now,
			LocalDateTime from, LocalDateTime before, LocalDateTime endsBy, boolean allTypes,
			Collection<MatchType> types, Pageable pageable);

	@Query("""
			select m from Match m
			join fetch m.courtSlot s
			join fetch s.court
			join fetch m.host
			where m.id = :id and s.status = :slotStatus and m.status = :matchStatus and s.startsAt > :now
			""")
	Optional<Match> findDiscoveryDetail(Long id, CourtSlotStatus slotStatus, MatchStatus matchStatus, LocalDateTime now);
}
