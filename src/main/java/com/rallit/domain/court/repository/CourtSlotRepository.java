package com.rallit.domain.court.repository;

import java.util.Optional;

import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.enums.EvidenceReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtSlotRepository extends JpaRepository<CourtSlot, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CourtSlot s where s.id = :id")
	Optional<CourtSlot> findByIdForUpdate(@Param("id") Long id);

	@Query(value = """
			select s from CourtSlot s
			join fetch s.court
			join fetch s.registrant
			where s.evidenceReviewStatus = :status
			order by s.createdAt, s.id
			""", countQuery = "select count(s) from CourtSlot s where s.evidenceReviewStatus = :status")
	Page<CourtSlot> findAllByEvidenceReviewStatus(@Param("status") EvidenceReviewStatus status, Pageable pageable);
}
