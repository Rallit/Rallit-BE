package com.rallit.global.security.oauth2;

import com.rallit.domain.auth.model.LoginTarget;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class PurposeAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
  private final DefaultOAuth2AuthorizationRequestResolver delegate;

  public PurposeAuthorizationRequestResolver(ClientRegistrationRepository repo) {
    delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/api/v1/auth");
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest r) {
    return com.rallit.global.security.web.AuthRequestMatchers.isLogin(r)
        ? customize(delegate.resolve(r), r)
        : null;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest r, String id) {
    return customize(delegate.resolve(r, id), r);
  }

  private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest a, HttpServletRequest r) {
    if (a == null) return null;
    String raw = r.getParameter("target");
    LoginTarget target =
        raw == null || raw.equals("service")
            ? LoginTarget.SERVICE
            : raw.equals("admin") ? LoginTarget.ADMIN : null;
    if (target == null) throw new OAuth2AuthorizationException(new OAuth2Error("invalid_request"));
    return OAuth2AuthorizationRequest.from(a)
        .attributes(v -> v.put(RedisAuthorizationRequestRepository.TARGET, target.name()))
        .build();
  }
}
