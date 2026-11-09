package com.rallit.global.security.authentication;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.user.enums.UserRole;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.global.security.web.SecurityErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerAuthenticationFilter extends OncePerRequestFilter {
  private final LoginTarget target;
  private final JwtTokenService jwt;
  private final UserRepository users;
  private final SecurityErrorWriter errors;

  public BearerAuthenticationFilter(
      LoginTarget t, JwtTokenService j, UserRepository u, SecurityErrorWriter e) {
    target = t;
    jwt = j;
    users = u;
    errors = e;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return com.rallit.global.security.web.AuthRequestMatchers.isRecovery(r, target)
        || com.rallit.global.security.web.AuthRequestMatchers.isOAuth(r);
  }

  private String accessToken(HttpServletRequest request) {
    var headers = java.util.Collections.list(request.getHeaders("Authorization"));
    if (headers.isEmpty()) return null;
    if (headers.size() != 1)
      throw new GeneralException(
          com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED);
    String header = headers.getFirst();
    if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7)
      throw new GeneralException(
          com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED);
    return header.substring(7);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    try {
      String token = accessToken(r);
      if (token != null) {
        var v = jwt.verifyAccess(token, target);
        var u =
            users
                .findById(v.userId())
                .orElseThrow(
                    () ->
                        new GeneralException(
                            com.rallit.global.apiPayload.code.status.ErrorStatus._UNAUTHORIZED));
        if (target == LoginTarget.ADMIN && u.getRole() != UserRole.ADMIN) {
          errors.write(s, 403);
          return;
        }
        var principal =
            new RallitPrincipal(u.getId(), target, u.getRole(), u.isOnboardingCompleted());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))));
        SecurityContextHolder.setContext(context);
      }
    } catch (GeneralException e) {
      SecurityContextHolder.clearContext();
      errors.write(s, 401);
      return;
    }
    s.setHeader("Cache-Control", "no-store");
    chain.doFilter(r, s);
  }
}
