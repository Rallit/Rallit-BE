package com.rallit.domain.auth.service;

import static org.assertj.core.api.Assertions.*;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.support.IntegrationTestSupport;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
class SocialRegistrationIntegrationTest extends IntegrationTestSupport {
  @Autowired LoginService login;
  @Autowired UserRegistrationService registration;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean UserRepository users;
  @Autowired JwtTokenService jwt;
  @Autowired JdbcTemplate jdbc;

  SocialIdentity identity(OAuthProvider provider, String subject, String email, boolean verified) {
    return new SocialIdentity(provider, subject, email, verified);
  }

  String id() {
    return UUID.randomUUID().toString();
  }

  @Test
  void emailCollisionIsRejectedInBothDirectionsWithoutCreatingAnotherUser() {
    for (var firstProvider : OAuthProvider.values()) {
      String subject = id(), email = id() + "@example.test";
      var first = identity(firstProvider, subject, email, true);
      var tokens = login.login(first, LoginTarget.SERVICE);
      var secondProvider =
          firstProvider == OAuthProvider.KAKAO ? OAuthProvider.GOOGLE : OAuthProvider.KAKAO;
      String other = id();
      assertThatThrownBy(
              () ->
                  login.login(
                      identity(secondProvider, other, email.toUpperCase(Locale.ROOT), true),
                      LoginTarget.SERVICE))
          .isInstanceOfSatisfying(
              SocialLoginRejectedException.class,
              e -> {
                assertThat(e.reason())
                    .isEqualTo(SocialLoginRejectedException.Reason.ACCOUNT_EXISTS);
                assertThat(e.existingProvider()).isEqualTo(firstProvider);
              });
      assertThat(users.findByProviderAndProviderUserId(secondProvider.name(), other)).isEmpty();
      assertThat(jwt.verifyAccess(tokens.accessToken(), LoginTarget.SERVICE).userId())
          .isEqualTo(
              users
                  .findByProviderAndProviderUserId(firstProvider.name(), subject)
                  .orElseThrow()
                  .getId());
    }
  }

  @Test
  void missingOrUnverifiedEmailCannotCreateMember() {
    for (var identity :
        List.of(
            identity(OAuthProvider.GOOGLE, id(), null, false),
            identity(OAuthProvider.KAKAO, id(), "some@example.test", false))) {
      assertThatThrownBy(() -> login.login(identity, LoginTarget.SERVICE))
          .isInstanceOfSatisfying(
              SocialLoginRejectedException.class,
              e ->
                  assertThat(e.reason())
                      .isEqualTo(SocialLoginRejectedException.Reason.EMAIL_REQUIRED));
      assertThat(
              users.findByProviderAndProviderUserId(identity.provider().name(), identity.subject()))
          .isEmpty();
    }
  }

  @Test
  void sameIdentityRetainsItsMemberAndLegacyEmailIsBackfilled() {
    String subject = id();
    jdbc.update(
        "insert into users(provider,provider_user_id,nickname,role,status,created_at,updated_at)"
            + " values('KAKAO',?,?,'USER','PENDING',now(),now())",
        subject,
        "L" + id().replace("-", "").substring(0, 8));
    var existing = users.findByProviderAndProviderUserId("KAKAO", subject).orElseThrow();
    var old = identity(OAuthProvider.KAKAO, subject, null, false);
    assertThat(
            jwt.verifyAccess(
                    login.login(old, LoginTarget.SERVICE).accessToken(), LoginTarget.SERVICE)
                .userId())
        .isEqualTo(existing.getId());
    String email = id() + "@example.test";
    login.login(identity(OAuthProvider.KAKAO, subject, email, true), LoginTarget.SERVICE);
    assertThat(users.findById(existing.getId()).orElseThrow().getEmail()).isEqualTo(email);
  }

  @Test
  void emailBackfillPreservesConcurrentRoleAndStatusChanges() {
    String subject = id();
    var member =
        registration.getOrCreateUser(
            identity(OAuthProvider.KAKAO, subject, subject + "@example.test", true));
    jdbc.update("update users set role='ADMIN', email=null where id=?", member.getId());
    var stale = users.findById(member.getId()).orElseThrow();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              jdbc.update(
                  "update users set role='USER', status='ACTIVE' where id=?", member.getId());
              return java.util.Optional.of(stale);
            })
        .when(users)
        .findByProviderAndProviderUserId("KAKAO", subject);
    try {
      registration.getOrCreateUser(
          identity(OAuthProvider.KAKAO, subject, subject + "@example.test", true));
      var saved = users.findById(member.getId()).orElseThrow();
      assertThat(saved.getRole()).isEqualTo(com.rallit.domain.user.enums.UserRole.USER);
      assertThat(saved.getStatus()).isEqualTo(com.rallit.domain.user.enums.UserStatus.ACTIVE);
      assertThat(saved.getEmail()).isEqualTo(subject + "@example.test");
    } finally {
      org.mockito.Mockito.reset(users);
    }
  }

  @Test
  void simultaneousDifferentProvidersCannotCreateDuplicateEmail() throws Exception {
    String email = id() + "@example.test";
    var first = identity(OAuthProvider.KAKAO, id(), email, true);
    var second = identity(OAuthProvider.GOOGLE, id(), email, true);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Boolean> a =
          () -> {
            gate.await();
            return attempt(first);
          };
      Callable<Boolean> b =
          () -> {
            gate.await();
            return attempt(second);
          };
      var fa = pool.submit(a);
      var fb = pool.submit(b);
      gate.countDown();
      assertThat(List.of(fa.get(), fb.get())).containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            jdbc.queryForObject("select count(*) from users where email_key=?", Long.class, email))
        .isEqualTo(1L);
  }

  @Test
  void simultaneousFirstLoginsWithSameIdentityReuseTheMember() throws Exception {
    String subject = id(), email = id() + "@example.test";
    var identity = identity(OAuthProvider.KAKAO, subject, email, true);
    var firstLookup = new CountDownLatch(1);
    var otherLoginCommitted = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    // Hold the first empty lookup until the other request has committed the same identity.
    org.mockito.Mockito.doAnswer(
            invocation -> {
              var matchingUser =
                  jdbc.query(
                          "select id from users where provider='KAKAO' and provider_user_id=?",
                          (row, index) -> row.getLong("id"),
                          subject)
                      .stream()
                      .findFirst()
                      .flatMap(users::findById);
              if (first.getAndSet(false)) {
                assertThat(matchingUser).isEmpty();
                firstLookup.countDown();
                assertThat(otherLoginCommitted.await(10, TimeUnit.SECONDS)).isTrue();
              }
              return matchingUser;
            })
        .when(users)
        .findByProviderAndProviderUserId("KAKAO", subject);

    try (var pool = Executors.newSingleThreadExecutor()) {
      var waitingLogin = pool.submit(() -> login.login(identity, LoginTarget.SERVICE));
      try {
        assertThat(firstLookup.await(10, TimeUnit.SECONDS)).isTrue();
        var committed = login.login(identity, LoginTarget.SERVICE);
        otherLoginCommitted.countDown();
        var resumed = waitingLogin.get(10, TimeUnit.SECONDS);

        assertThat(jwt.verifyAccess(resumed.accessToken(), LoginTarget.SERVICE).userId())
            .isEqualTo(jwt.verifyAccess(committed.accessToken(), LoginTarget.SERVICE).userId());
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from users where email_key=?", Long.class, email))
            .isEqualTo(1L);
      } finally {
        otherLoginCommitted.countDown();
      }
    } finally {
      org.mockito.Mockito.reset(users);
    }
  }

  boolean attempt(SocialIdentity identity) {
    try {
      registration.getOrCreateUser(identity);
      return true;
    } catch (SocialLoginRejectedException rejected) {
      assertThat(rejected.reason()).isEqualTo(SocialLoginRejectedException.Reason.ACCOUNT_EXISTS);
      return false;
    }
  }
}
