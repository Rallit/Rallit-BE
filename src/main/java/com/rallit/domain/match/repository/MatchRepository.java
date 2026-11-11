package com.rallit.domain.match.repository;

import com.rallit.domain.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
}
