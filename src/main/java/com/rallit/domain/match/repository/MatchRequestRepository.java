package com.rallit.domain.match.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.match.entity.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

	long countByOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(Long ownerId, LocalDateTime now);

	List<MatchRequest> findAllByOwnerIdAndDeletedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
			Long ownerId, LocalDateTime now);

	Optional<MatchRequest> findByIdAndOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(
			Long id, Long ownerId, LocalDateTime now);
}
