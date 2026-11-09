package com.rallit.domain.auth.service;

import static org.assertj.core.api.Assertions.*;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
class LoginServiceIntegrationTest extends IntegrationTestSupport {
  @Autowired LoginService login;
  @Autowired UserRepository users;
  @Autowired JwtTokenService jwt;
  @Autowired JdbcTemplate jdbc;

  @Test
  void serviceLoginCreatesOneStableMemberAndIndependentSessions() {
    String kakaoId = UUID.randomUUID().toString();
    var first = login.loginKakao(kakaoId, LoginTarget.SERVICE);
    var second = login.loginKakao(kakaoId, LoginTarget.SERVICE);
    var user = users.findByProviderAndProviderUserId("KAKAO", kakaoId).orElseThrow();
    assertThat(user.isOnboardingCompleted()).isFalse();
    assertThat(jwt.verifyAccess(first.accessToken(), LoginTarget.SERVICE).userId())
        .isEqualTo(user.getId());
    assertThat(jwt.verifyAccess(second.accessToken(), LoginTarget.SERVICE).userId())
        .isEqualTo(user.getId());
    assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
  }

  @Test
  void adminLoginCannotCreateAnAccountOrPromoteRegularMember() {
    String kakaoId = UUID.randomUUID().toString();
    assertThatThrownBy(() -> login.loginKakao(kakaoId, LoginTarget.ADMIN))
        .isInstanceOfSatisfying(
            GeneralException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorStatus._FORBIDDEN));
    assertThat(users.findByProviderAndProviderUserId("KAKAO", kakaoId)).isEmpty();
    login.loginKakao(kakaoId, LoginTarget.SERVICE);
    assertThatThrownBy(() -> login.loginKakao(kakaoId, LoginTarget.ADMIN))
        .isInstanceOfSatisfying(
            GeneralException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorStatus._FORBIDDEN));
  }

  @Test
  void knownAdminCanLogInWithoutTennisProfile() {
    String kakaoId = UUID.randomUUID().toString();
    login.loginKakao(kakaoId, LoginTarget.SERVICE);
    long id = users.findByProviderAndProviderUserId("KAKAO", kakaoId).orElseThrow().getId();
    jdbc.update("update users set role='ADMIN' where id=?", id);
    var tokens = login.loginKakao(kakaoId, LoginTarget.ADMIN);
    assertThat(jwt.verifyAccess(tokens.accessToken(), LoginTarget.ADMIN).userId()).isEqualTo(id);
  }
}
