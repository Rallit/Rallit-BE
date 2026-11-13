package com.rallit.domain.match.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchApplicationRepository extends JpaRepository<MatchApplication, Long> {

	Collection<MatchApplicationStatus> ACTIVE_STATUSES = EnumSet.of(MatchApplicationStatus.PENDING,
			MatchApplicationStatus.PAYMENT_PENDING, MatchApplicationStatus.CONFIRMED);

	Collection<MatchApplicationStatus> CLOSED_STATUSES = EnumSet.of(MatchApplicationStatus.REJECTED,
			MatchApplicationStatus.CANCELED, MatchApplicationStatus.EXPIRED);

	Collection<MatchApplicationStatus> SEAT_OCCUPYING_STATUSES = EnumSet.of(MatchApplicationStatus.PAYMENT_PENDING,
			MatchApplicationStatus.CONFIRMED);

	boolean existsByMatchIdAndApplicantIdAndStatusIn(Long matchId, Long applicantId,
			Collection<MatchApplicationStatus> statuses);

	default boolean existsActiveApplication(Long matchId, Long applicantId) {
		return existsByMatchIdAndApplicantIdAndStatusIn(matchId, applicantId, ACTIVE_STATUSES);
	}

	@Query("""
			select a from MatchApplication a
			join fetch a.match m
			join fetch m.courtSlot s
			join fetch s.court
			where a.applicant.id = :applicantId and a.status in :statuses
			and s.startsAt >= :from and (cast(:to as LocalDateTime) is null or s.startsAt < :to)
			order by s.startsAt, a.id
			""")
	Slice<MatchApplication> findScheduledSliceByApplicantId(@Param("applicantId") Long applicantId,
			@Param("statuses") Collection<MatchApplicationStatus> statuses, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to, Pageable pageable);

	@Query("""
			select a from MatchApplication a
			join fetch a.match m
			join fetch m.courtSlot s
			join fetch s.court
			where a.applicant.id = :applicantId and a.status in :statuses
			order by a.updatedAt desc, a.id desc
			""")
	Slice<MatchApplication> findRecentSliceByApplicantId(@Param("applicantId") Long applicantId,
			@Param("statuses") Collection<MatchApplicationStatus> statuses, Pageable pageable);

	@Query("""
			select a.match.id as matchId, count(a) as count from MatchApplication a
			where a.match.id in :matchIds and a.status in :statuses
			group by a.match.id
			""")
	List<MatchApplicationCount> countByMatchIdsAndStatuses(@Param("matchIds") Collection<Long> matchIds,
			@Param("statuses") Collection<MatchApplicationStatus> statuses);

	@Query("""
			select a from MatchApplication a
			join fetch a.match m
			join fetch m.host
			left join fetch m.matchTypes
			join fetch m.courtSlot s
			join fetch s.court
			where a.id = :id and a.applicant.id = :applicantId
			""")
	Optional<MatchApplication> findDetailByIdAndApplicantId(@Param("id") Long id,
			@Param("applicantId") Long applicantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from MatchApplication a where a.id = :id and a.applicant.id = :applicantId")
	Optional<MatchApplication> findByIdAndApplicantIdForUpdate(@Param("id") Long id,
			@Param("applicantId") Long applicantId);

	@Query("""
			select a from MatchApplication a
			join fetch a.applicant
			where a.match.id = :matchId and a.status = :status
			order by a.id
			""")
	List<MatchApplication> findAllWithApplicantByMatchIdAndStatus(@Param("matchId") Long matchId,
			@Param("status") MatchApplicationStatus status);

	interface MatchApplicationCount {

		Long getMatchId();

		long getCount();
	}
}
