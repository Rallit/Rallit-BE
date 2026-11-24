package com.rallit.domain.court.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.config.KakaoLocalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CourtSearchRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final RedisScript<Long> INCREMENT = RedisScript.of("""
			local count = redis.call('incr', KEYS[1])
			if count == 1 then redis.call('pexpire', KEYS[1], ARGV[1]) end
			return count
			""", Long.class);

	private final StringRedisTemplate redis;
	private final KakaoLocalProperties properties;
	private final Clock clock;

	void acquire(Long userId) {
		String key = "court:search:" + userId + ":" + clock.millis() / WINDOW.toMillis();
		Long count;
		try {
			count = redis.execute(INCREMENT, List.of(key), String.valueOf(WINDOW.toMillis()));
		} catch (DataAccessException failure) {
			log.warn("Court search rate limit is unavailable", failure);
			return;
		}
		if (count != null && count > properties.requestsPerMinute()) {
			throw new GeneralException(CourtErrorStatus.COURT_SEARCH_RATE_LIMITED);
		}
	}
}
