package com.rallit.global.security.config;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.security.cookie.AuthCookieManager;
import com.rallit.global.security.handler.SecurityErrorWriter;
import com.rallit.global.security.jwt.*;
import com.rallit.global.security.oauth2.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.*;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ClientRegistrationRepository clientRegistrations(AuthProperties p) {
    var builder =
        ClientRegistration.withRegistrationId("kakao")
            .clientId(p.kakaoClientId())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(p.kakaoRedirectUri())
            .authorizationUri(
                p.kakaoAuthorizationUri() == null
                    ? "https://kauth.kakao.com/oauth/authorize"
                    : p.kakaoAuthorizationUri())
            .tokenUri(
                p.kakaoTokenUri() == null
                    ? "https://kauth.kakao.com/oauth/token"
                    : p.kakaoTokenUri())
            .userInfoUri(
                p.kakaoUserInfoUri() == null
                    ? "https://kapi.kakao.com/v2/user/me"
                    : p.kakaoUserInfoUri())
            .userNameAttributeName("id")
            .clientName("Kakao");
    if (p.kakaoClientSecret() != null && !p.kakaoClientSecret().isBlank())
      builder
          .clientSecret(p.kakaoClientSecret())
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    else builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
    return new InMemoryClientRegistrationRepository(builder.build());
  }

  private void common(
      HttpSecurity http,
      LoginTarget target,
      AuthProperties p,
      JwtTokenService jwt,
      UserRepository users,
      AuthCookieManager cookies,
      SecurityErrorWriter errors)
      throws Exception {
    http.addFilterAfter(
        new com.rallit.global.security.cookie.CookieBoundaryFilter(target, p, cookies, errors),
        org.springframework.web.filter.CorsFilter.class);
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(
            s ->
                s.configurationSource(
                    r -> {
                      var c = new CorsConfiguration();
                      c.setAllowedOrigins(p.origins(target == LoginTarget.ADMIN));
                      c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                      c.setAllowedHeaders(
                          List.of("Content-Type", "Authorization", "X-Requested-With"));
                      c.setAllowCredentials(true);
                      return c;
                    }))
        .exceptionHandling(
            s ->
                s.authenticationEntryPoint((r, response, e) -> errors.write(response, 401))
                    .accessDeniedHandler(errors::denied))
        .addFilterBefore(
            new BearerAuthenticationFilter(target, jwt, users, errors),
            AnonymousAuthenticationFilter.class);
  }

  @Bean
  @Order(1)
  SecurityFilterChain admin(
      HttpSecurity http,
      AuthProperties p,
      JwtTokenService jwt,
      UserRepository users,
      AuthCookieManager cookies,
      SecurityErrorWriter errors)
      throws Exception {
    common(http, LoginTarget.ADMIN, p, jwt, users, cookies, errors);
    return http.securityMatcher("/api/v1/admin/**")
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/admin/auth/refresh", "/api/v1/admin/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/me")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain service(
      HttpSecurity http,
      AuthProperties p,
      JwtTokenService jwt,
      UserRepository users,
      AuthCookieManager cookies,
      SecurityErrorWriter errors,
      ClientRegistrationRepository clients,
      RedisAuthorizationRequestRepository requests,
      OAuthLoginHandlers handlers,
      org.springframework.core.env.Environment environment)
      throws Exception {
    common(http, LoginTarget.SERVICE, p, jwt, users, cookies, errors);
    http.addFilterBefore(
        new org.springframework.web.filter.OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              jakarta.servlet.http.HttpServletRequest r,
              jakarta.servlet.http.HttpServletResponse response,
              jakarta.servlet.FilterChain chain)
              throws jakarta.servlet.ServletException, java.io.IOException {
            String t = r.getParameter("target");
            if (com.rallit.global.security.route.AuthRequestMatchers.isKakaoLogin(r)
                && t != null
                && !t.equals("service")
                && !t.equals("admin")) {
              errors.write(response, 400);
              return;
            }
            chain.doFilter(r, response);
          }
        },
        org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
            .class);
    http.oauth2Login(
        o ->
            o.authorizationEndpoint(
                    a ->
                        a.authorizationRequestResolver(
                                new PurposeAuthorizationRequestResolver(clients))
                            .authorizationRequestRepository(requests))
                .redirectionEndpoint(r -> r.baseUri("/api/v1/auth/kakao/callback"))
                .authorizedClientRepository(new NoStoreAuthorizedClientRepository())
                .successHandler(handlers::success)
                .failureHandler(handlers::authenticationFailure));
    if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))) {
      http.authorizeHttpRequests(
          a ->
              a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                  .permitAll());
    }
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/kakao",
                        "/api/v1/auth/kakao/callback",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/actuator/health",
                        "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/users/me")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
