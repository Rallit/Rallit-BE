package com.rallit.global.security.oauth2;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.global.security.web.AuthCookieManager;
import jakarta.servlet.http.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisAuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
  public static final String TARGET = "rallit.loginTarget";
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final AuthCookieManager cookies;
  private final com.rallit.global.config.AuthProperties properties;

  private String name(String state) {
    return (properties.secure() ? "__Host-rallit_oauth_" : "rallit_oauth_")
        + TokenSessionService.hash(state).substring(0, 20);
  }

  private String key(String state) {
    return "auth:oauth:" + TokenSessionService.hash(state);
  }

  public record Snapshot(
      String browserHash,
      String authorizationUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> attributes,
      Map<String, Object> additionalParameters,
      String authorizationRequestUri) {}

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest auth, HttpServletRequest request, HttpServletResponse response) {
    if (auth == null) return;
    String binding = UUID.randomUUID() + "-" + UUID.randomUUID();
    var snapshot =
        new Snapshot(
            TokenSessionService.hash(binding),
            auth.getAuthorizationUri(),
            auth.getClientId(),
            auth.getRedirectUri(),
            auth.getScopes(),
            auth.getState(),
            auth.getAttributes(),
            auth.getAdditionalParameters(),
            auth.getAuthorizationRequestUri());
    redis
        .opsForValue()
        .set(key(auth.getState()), mapper.writeValueAsString(snapshot), Duration.ofMinutes(5));
    cookies.add(
        response,
        name(auth.getState()),
        binding,
        (properties.secure() ? "/" : "/api/v1/auth/kakao/callback"),
        300);
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return load(request, null, false);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    return load(request, response, true);
  }

  private OAuth2AuthorizationRequest load(
      HttpServletRequest request, HttpServletResponse response, boolean consume) {
    String state = request.getParameter("state");
    if (state == null || state.length() > 512) return null;
    String binding = cookies.read(request, name(state));
    if (binding == null) return null;
    String raw = redis.opsForValue().get(key(state));
    if (raw == null) return null;
    Snapshot s = mapper.readValue(raw, Snapshot.class);
    if (!java.security.MessageDigest.isEqual(
        s.browserHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        TokenSessionService.hash(binding).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      return null;
    if (consume) {
      Long deleted =
          redis.execute(
              new DefaultRedisScript<>(
                  "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else"
                      + " return 0 end",
                  Long.class),
              List.of(key(state)),
              raw);
      if (!Long.valueOf(1).equals(deleted)) return null;
      request.setAttribute(TARGET, LoginTarget.valueOf((String) s.attributes().get(TARGET)));
      cookies.add(
          response,
          name(state),
          "",
          (properties.secure() ? "/" : "/api/v1/auth/kakao/callback"),
          0);
    }
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(s.authorizationUri())
        .clientId(s.clientId())
        .redirectUri(s.redirectUri())
        .scopes(s.scopes())
        .state(s.state())
        .attributes(a -> a.putAll(s.attributes()))
        .additionalParameters(a -> a.putAll(s.additionalParameters()))
        .authorizationRequestUri(s.authorizationRequestUri())
        .build();
  }
}
