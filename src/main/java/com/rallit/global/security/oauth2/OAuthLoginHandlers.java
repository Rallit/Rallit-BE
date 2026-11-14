package com.rallit.global.security.oauth2;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.auth.service.LoginService;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.config.AuthProperties;
import com.rallit.global.security.cookie.AuthCookieManager;
import com.rallit.global.security.oauth2.google.GoogleIdentityMapper;
import com.rallit.global.security.oauth2.kakao.KakaoIdentityMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginHandlers {
  private final AuthProperties properties;
  private final LoginService login;
  private final AuthCookieManager cookies;

  public void success(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    Object saved = request.getAttribute(RedisAuthorizationRequestRepository.TARGET);
    if (!(saved instanceof LoginTarget target)) {
      failure(request, response);
      return;
    }
    LoginStage stage = LoginStage.IDENTITY;
    try {
      SocialIdentity identity = identity((OAuth2AuthenticationToken) authentication);
      stage = LoginStage.LOGIN;
      var tokens = login.login(identity, target);
      stage = LoginStage.RESPONSE;
      cookies.writeRefresh(response, tokens, target);
      response.setHeader("Cache-Control", "no-store");
      response.sendRedirect(destination(request, target));
    } catch (SocialLoginRejectedException rejected) {
      String error =
          switch (rejected.reason()) {
            case EMAIL_REQUIRED -> "email_required";
            case ACCOUNT_EXISTS ->
                "account_exists&existingProvider=" + rejected.existingProvider().registrationId();
          };
      response.setHeader("Cache-Control", "no-store");
      response.sendRedirect(destination(request, target) + "?error=" + error);
    } catch (GeneralException rejected) {
      if (rejected.getCode() == ErrorStatus._UNAUTHORIZED
          || rejected.getCode() == ErrorStatus._FORBIDDEN) {
        log.warn(
            "OAuth login rejected: target={}, stage={}, code={}",
            target,
            stage,
            rejected.getCode().getReason().getCode());
      } else {
        log.error(
            "OAuth login failed: target={}, stage={}, code={}, causeType={}",
            target,
            stage,
            rejected.getCode().getReason().getCode(),
            causeType(rejected));
      }
      failure(request, response);
    } catch (DataAccessException unavailable) {
      log.error(
          "OAuth persistence unavailable: target={}, stage={}, failureType={}",
          target,
          stage,
          unavailable.getClass().getSimpleName());
      failure(request, response);
    } catch (RuntimeException unexpected) {
      log.error(
          "OAuth unexpected failure: target={}, stage={}, failureType={}",
          target,
          stage,
          unexpected.getClass().getSimpleName());
      failure(request, response);
    }
  }

  public void failure(HttpServletRequest request, HttpServletResponse response) throws IOException {
    var target =
        request.getAttribute(RedisAuthorizationRequestRepository.TARGET) instanceof LoginTarget t
            ? t
            : LoginTarget.SERVICE;
    String destination = destination(request, target);
    response.setHeader("Cache-Control", "no-store");
    response.sendRedirect(destination + "?error=login_failed");
  }

  private String destination(HttpServletRequest request, LoginTarget target) {
    if (target == LoginTarget.SERVICE
        && request.getAttribute(RedisAuthorizationRequestRepository.FRONTEND_CALLBACK)
            instanceof String callback) {
      return callback;
    }
    return properties.callbackUrl(target);
  }

  public void authenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    log.warn("OAuth exchange rejected: failureType={}", failure.getClass().getSimpleName());
    failure(request, response);
  }

  public void authorizationRequestFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    if (failure.getCause() instanceof OAuth2AuthenticationException oauthFailure
        && RedisAuthorizationRequestRepository.STORE_UNAVAILABLE.equals(
            oauthFailure.getError().getErrorCode())) {
      log.error("OAuth state storage unavailable: causeType={}", causeType(oauthFailure));
      failure(request, response);
      return;
    }
    log.error("OAuth authorization request failed: causeType={}", causeType(failure));
    response.sendError(
        HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
  }

  private SocialIdentity identity(OAuth2AuthenticationToken authentication) {
    OAuthProvider provider =
        OAuthProvider.fromRegistrationId(authentication.getAuthorizedClientRegistrationId());
    OAuth2User principal = authentication.getPrincipal();
    return switch (provider) {
      case KAKAO -> KakaoIdentityMapper.map(principal);
      case GOOGLE -> GoogleIdentityMapper.map(principal);
    };
  }

  private String causeType(Throwable failure) {
    return failure.getCause() == null ? "none" : failure.getCause().getClass().getSimpleName();
  }

  private enum LoginStage {
    IDENTITY,
    LOGIN,
    RESPONSE
  }
}
