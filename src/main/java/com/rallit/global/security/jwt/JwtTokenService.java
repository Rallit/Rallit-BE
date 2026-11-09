package com.rallit.global.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rallit.domain.auth.model.*;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.config.AuthProperties;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
  private final EnumMap<LoginTarget, SecretKeySpec> keys = new EnumMap<>(LoginTarget.class);
  private final Clock clock;

  public JwtTokenService(AuthProperties p, Clock clock) {
    this.clock = clock;
    keys.put(LoginTarget.SERVICE, key(p.serviceKey()));
    keys.put(LoginTarget.ADMIN, key(p.adminKey()));
    if (Arrays.equals(
        keys.get(LoginTarget.SERVICE).getEncoded(), keys.get(LoginTarget.ADMIN).getEncoded()))
      throw new IllegalArgumentException("Separate authentication signing keys required");
  }

  private SecretKeySpec key(String encoded) {
    byte[] bytes = Base64.getDecoder().decode(encoded);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT key must contain at least 256 bits");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  public SessionTokens issue(Long id, UUID sid, LoginTarget target, Instant deadline) {
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    deadline = deadline.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    if (!deadline.isAfter(now)) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    Instant accessEnd = now.plusSeconds(1800).isBefore(deadline) ? now.plusSeconds(1800) : deadline;
    return new SessionTokens(
        encode(id, sid, target, "access", now, accessEnd),
        encode(id, sid, target, "refresh", now, deadline),
        accessEnd,
        deadline);
  }

  private String encode(
      Long id, UUID sid, LoginTarget target, String type, Instant now, Instant end) {
    var claims =
        JwtClaimsSet.builder()
            .issuer("rallit")
            .subject(id.toString())
            .audience(List.of(target.audience()))
            .issuedAt(now)
            .expiresAt(end)
            .id(UUID.randomUUID().toString())
            .claim("sid", sid.toString())
            .claim("token_type", type)
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(keys.get(target)))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public VerifiedAccess verifyAccess(String token, LoginTarget target) {
    var j = verify(token, target, "access");
    return new VerifiedAccess(
        Long.parseLong(j.getSubject()),
        UUID.fromString(j.getClaimAsString("sid")),
        j.getExpiresAt());
  }

  public VerifiedRefresh verifyRefresh(String token, LoginTarget target) {
    var j = verify(token, target, "refresh");
    return new VerifiedRefresh(
        Long.parseLong(j.getSubject()),
        UUID.fromString(j.getClaimAsString("sid")),
        j.getExpiresAt());
  }

  private Jwt verify(String token, LoginTarget target, String type) {
    try {
      var decoder =
          NimbusJwtDecoder.withSecretKey(keys.get(target)).macAlgorithm(MacAlgorithm.HS256).build();
      var timestamp =
          new org.springframework.security.oauth2.jwt.JwtTimestampValidator(Duration.ZERO);
      timestamp.setClock(clock);
      decoder.setJwtValidator(
          new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
              timestamp, new JwtIssuerValidator("rallit")));
      Jwt j = decoder.decode(token);
      if (!List.of(target.audience()).equals(j.getAudience())
          || !type.equals(j.getClaimAsString("token_type"))
          || j.getExpiresAt() == null
          || !j.getExpiresAt().isAfter(clock.instant())
          || Long.parseLong(j.getSubject()) <= 0) throw new IllegalArgumentException();
      String sessionId = j.getClaimAsString("sid");
      if (sessionId == null) throw new IllegalArgumentException("Missing session claim");
      UUID.fromString(sessionId);
      return j;
    } catch (JwtException | IllegalArgumentException e) {
      throw new GeneralException(ErrorStatus._UNAUTHORIZED, e);
    }
  }

  public record VerifiedAccess(Long userId, UUID sid, Instant expiresAt) {}

  public record VerifiedRefresh(Long userId, UUID sid, Instant expiresAt) {}
}
