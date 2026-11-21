package com.rallit.domain.match.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.match.entity.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

	long countByOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(Long ownerId, Instant now);

	List<MatchRequest> findAllByOwnerIdAndDeletedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
			Long ownerId, Instant now);

	Optional<MatchRequest> findByIdAndOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(
			Long id, Long ownerId, Instant now);
}
