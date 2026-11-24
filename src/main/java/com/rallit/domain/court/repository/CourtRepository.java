package com.rallit.domain.court.repository;

import com.rallit.domain.court.entity.Court;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtRepository extends JpaRepository<Court, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select court from Court court where court.id = :id")
  Optional<Court> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = "area")
  Optional<Court> findByKakaoPlaceId(String kakaoPlaceId);
}
