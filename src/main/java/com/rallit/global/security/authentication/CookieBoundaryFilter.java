package com.rallit.global.security.authentication;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.config.AuthProperties;
import com.rallit.global.security.cookie.AuthCookieManager;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects cookie-authenticated recovery operations without a CSRF-token endpoint. */
public class CookieBoundaryFilter extends OncePerRequestFilter {
  private final LoginTarget target;
  private final AuthProperties properties;
  private final AuthCookieManager cookies;
  private final SecurityErrorWriter errors;

  public CookieBoundaryFilter(
      LoginTarget target,
      AuthProperties properties,
      AuthCookieManager cookies,
      SecurityErrorWriter errors) {
    this.target = target;
    this.properties = properties;
    this.cookies = cookies;
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setHeader("Cache-Control", "no-store");
    boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    boolean cookieOperation = isCookieOperation(request);
    if (unsafe && (target == LoginTarget.ADMIN || cookieOperation) && !hasAllowedOrigin(request)) {
      errors.write(response, 403);
      return;
    }
    if (cookieOperation && !"OPTIONS".equals(request.getMethod())) {
      if (!"POST".equals(request.getMethod())
          || !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
          || !isJson(request)) {
        errors.write(response, 403);
        return;
      }
      try {
        cookies.read(request, cookies.name(target, "refresh"));
      } catch (GeneralException invalid) {
        errors.write(response, 401);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private boolean isCookieOperation(HttpServletRequest request) {
    return AuthRequestMatchers.isRecovery(request, target);
  }

  private boolean hasAllowedOrigin(HttpServletRequest request) {
    var origins = Collections.list(request.getHeaders("Origin"));
    return origins.size() == 1
        && properties.origins(target == LoginTarget.ADMIN).contains(origins.getFirst());
  }

  private boolean isJson(HttpServletRequest request) {
    try {
      return request.getContentType() != null
          && MediaType.APPLICATION_JSON.includes(
              MediaType.parseMediaType(request.getContentType()));
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }
}
