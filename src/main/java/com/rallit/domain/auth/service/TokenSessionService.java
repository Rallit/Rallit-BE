package com.rallit.domain.auth.service;

import com.rallit.domain.auth.dto.internal.SessionTokens;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.user.enums.UserRole;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.global.security.jwt.JwtTokenService.VerifiedAccess;
import com.rallit.global.security.jwt.JwtTokenService.VerifiedRefresh;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenSessionService {
  private static final Duration REFRESH_REUSE_GRACE = Duration.ofSeconds(5);

  private final JwtTokenService jwt;
  private final StringRedisTemplate redis;
  private final UserRepository users;
  private final Clock clock;

  private String key(UUID sid, LoginTarget t) {
    return "auth:" + t.name().toLowerCase(Locale.ROOT) + ":session:" + sid;
  }

  private void allowed(Long id, LoginTarget target) {
    var u = users.findById(id).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
    if (u.isWithdrawn()) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    if (target == LoginTarget.ADMIN && u.getRole() != UserRole.ADMIN)
      throw new GeneralException(ErrorStatus._FORBIDDEN);
  }

  public SessionTokens login(Long id, LoginTarget target) {
    allowed(id, target);
    UUID sid = UUID.randomUUID();
    var pair = jwt.issue(id, sid, target, clock.instant().plusSeconds(14 * 86400L));
    String value = id + ":" + hash(pair.refreshToken());
    try {
      redis
          .opsForValue()
          .set(key(sid, target), value, Duration.between(clock.instant(), pair.sessionExpiresAt()));
    } catch (DataAccessException e) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, e);
    }
    return pair;
  }

  public SessionTokens refresh(String token, LoginTarget target) {
    var verified = jwt.verifyRefresh(token, target);
    allowed(verified.userId(), target);
    var pair = jwt.issue(verified.userId(), verified.sid(), target, verified.expiresAt());
    long rotated =
        execute(
            """
            local current = redis.call('get', KEYS[1])
            if not current then return 0 end
            if current == ARGV[1] then
              redis.call('set', KEYS[2], ARGV[1], 'PX', ARGV[3])
              redis.call('set', KEYS[1], ARGV[2], 'KEEPTTL')
              return 1
            end
            if redis.call('get', KEYS[2]) ~= ARGV[1] then
              redis.call('del', KEYS[1], KEYS[2])
            end
            return 0
            """,
            verified.sid(),
            target,
            verified.userId() + ":" + hash(token),
            verified.userId() + ":" + hash(pair.refreshToken()),
            String.valueOf(REFRESH_REUSE_GRACE.toMillis()));
    if (rotated != 1) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    return pair;
  }

  public void requireActiveSession(VerifiedAccess access, LoginTarget target) {
    try {
      if (!Boolean.TRUE.equals(redis.hasKey(key(access.sid(), target))))
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    } catch (DataAccessException e) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, e);
    }
  }

  public void logout(String token, LoginTarget target) {
    if (token == null) return;

    VerifiedRefresh verified;
    try {
      verified = jwt.verifyRefresh(token, target);
    } catch (GeneralException invalidToken) {
      if (invalidToken.getCode() != ErrorStatus._UNAUTHORIZED) throw invalidToken;
      return;
    }

    long result =
        execute(
            """
            local current = redis.call('get', KEYS[1])
            if not current then return 0 end
            if current ~= ARGV[1] then return -1 end
            return redis.call('del', KEYS[1], KEYS[2])
            """,
            verified.sid(),
            target,
            verified.userId() + ":" + hash(token));
    if (result == -1) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
  }

  private long execute(String script, UUID sid, LoginTarget target, Object... arguments) {
    String sessionKey = key(sid, target);
    try {
      Long result =
          redis.execute(
              new DefaultRedisScript<>(script, Long.class),
              List.of(sessionKey, sessionKey + ":refresh-grace"),
              arguments);
      if (result == null) throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE);
      return result;
    } catch (DataAccessException e) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, e);
    }
  }

  public static String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
