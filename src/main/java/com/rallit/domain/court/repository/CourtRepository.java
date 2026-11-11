package com.rallit.domain.court.repository;

import com.rallit.domain.court.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtRepository extends JpaRepository<Court, Long> {
}
