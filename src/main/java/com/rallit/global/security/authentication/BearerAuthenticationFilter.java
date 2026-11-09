package com.rallit.global.security.authentication;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.jwt.JwtTokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerAuthenticationFilter extends OncePerRequestFilter {
  private final LoginTarget target;
  private final JwtTokenService jwt;
  private final UserDetailsService userDetailsService;
  private final SecurityErrorWriter errors;

  public BearerAuthenticationFilter(
      LoginTarget target,
      JwtTokenService jwt,
      UserDetailsService userDetailsService,
      SecurityErrorWriter errors) {
    this.target = target;
    this.jwt = jwt;
    this.userDetailsService = userDetailsService;
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return AuthRequestMatchers.isRecovery(request, target) || AuthRequestMatchers.isOAuth(request);
  }

  private String accessToken(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders("Authorization"));
    if (headers.isEmpty()) return null;
    if (headers.size() != 1) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    String header = headers.getFirst();
    if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7)
      throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    return header.substring(7);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      String token = accessToken(request);
      if (token != null) {
        var verified = jwt.verifyAccess(token, target);
        var principal = userDetailsService.loadUserByUsername(verified.userId().toString());
        if (target == LoginTarget.ADMIN
            && !principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
          errors.write(response, 403);
          return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
      }
    } catch (GeneralException | UsernameNotFoundException rejected) {
      SecurityContextHolder.clearContext();
      errors.write(response, 401);
      return;
    }
    response.setHeader("Cache-Control", "no-store");
    chain.doFilter(request, response);
  }
}
