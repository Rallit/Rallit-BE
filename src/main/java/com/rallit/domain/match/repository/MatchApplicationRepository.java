package com.rallit.domain.match.repository;

import com.rallit.domain.match.entity.MatchApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchApplicationRepository extends JpaRepository<MatchApplication, Long> {
}
