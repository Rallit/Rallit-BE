package com.rallit.global.security.oauth2;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.global.config.AuthProperties;
import com.rallit.global.security.authentication.AuthRequestMatchers;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class PurposeAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
  private final DefaultOAuth2AuthorizationRequestResolver delegate;
  private final AuthProperties properties;

  public PurposeAuthorizationRequestResolver(
      ClientRegistrationRepository repo, AuthProperties properties) {
    delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/api/v1/auth");
    this.properties = properties;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest r) {
    return AuthRequestMatchers.isLogin(r) ? customize(delegate.resolve(r), r) : null;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest r, String id) {
    return customize(delegate.resolve(r, id), r);
  }

  private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest a, HttpServletRequest r) {
    if (a == null) return null;
    LoginTarget target =
        LoginTarget.fromParameter(r.getParameter("target"))
            .orElseThrow(() -> new OAuth2AuthorizationException(new OAuth2Error("invalid_request")));
    String[] callbacks = r.getParameterValues("callback");
    return OAuth2AuthorizationRequest.from(a)
        .attributes(
            attributes -> {
              attributes.put(RedisAuthorizationRequestRepository.TARGET, target.name());
              if (target == LoginTarget.SERVICE
                  && callbacks != null
                  && callbacks.length == 1
                  && properties.allowsServiceCallback(callbacks[0])) {
                attributes.put(RedisAuthorizationRequestRepository.FRONTEND_CALLBACK, callbacks[0]);
              }
            })
        .build();
  }
}
