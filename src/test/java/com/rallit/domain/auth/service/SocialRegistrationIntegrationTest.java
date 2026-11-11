package com.rallit.domain.auth.service;

import static org.assertj.core.api.Assertions.*;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.model.*;
import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.SocialProvider;
import com.rallit.domain.user.repository.SocialAccountRepository;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.global.security.jwt.JwtTokenService;
import com.rallit.support.IntegrationTestSupport;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
class SocialRegistrationIntegrationTest extends IntegrationTestSupport {
  @Autowired LoginService login;
  @Autowired UserRegistrationService registration;
  @Autowired UserRepository users;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SocialAccountRepository accounts;
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
      jdbc.update(
          "update users set email = upper(email) where id = ?",
          jwt.verifyAccess(tokens.accessToken(), LoginTarget.SERVICE).userId());
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
      assertThat(
              accounts.findUserByProviderAndProviderUserId(
                  SocialProvider.valueOf(secondProvider.name()), other))
          .isEmpty();
      assertThat(jwt.verifyAccess(tokens.accessToken(), LoginTarget.SERVICE).userId())
          .isEqualTo(
              accounts
                  .findUserByProviderAndProviderUserId(
                      SocialProvider.valueOf(firstProvider.name()), subject)
                  .orElseThrow()
                  .getId());
    }
  }

  @Test
  void addressesWithDifferentDatabaseEmailKeysCanRegisterSeparately() {
    String prefix = id();
    var first =
        registration.getOrCreateUser(
            identity(OAuthProvider.GOOGLE, id(), prefix + "ς@example.test", true));
    var second =
        registration.getOrCreateUser(
            identity(OAuthProvider.KAKAO, id(), prefix + "σ@example.test", true));

    assertThat(second.getId()).isNotEqualTo(first.getId());
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
              accounts.findUserByProviderAndProviderUserId(
                  SocialProvider.valueOf(identity.provider().name()), identity.subject()))
          .isEmpty();
    }
  }

  @Test
  void sameIdentityRetainsItsMemberAndLegacyEmailIsBackfilled() {
    String subject = id();
    var existing = users.saveAndFlush(User.signUp(null));
    accounts.saveAndFlush(SocialAccount.of(existing, SocialProvider.KAKAO, subject));
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
                  "update users set role='USER', status='ACTIVE', gender='FEMALE' where id=?", member.getId());
              return java.util.Optional.of(stale);
            })
        .when(accounts)
        .findUserByProviderAndProviderUserId(SocialProvider.KAKAO, subject);
    try {
      registration.getOrCreateUser(
          identity(OAuthProvider.KAKAO, subject, subject + "@example.test", true));
      var saved = users.findById(member.getId()).orElseThrow();
      assertThat(saved.getRole()).isEqualTo(com.rallit.domain.user.enums.UserRole.USER);
      assertThat(saved.getStatus()).isEqualTo(com.rallit.domain.user.enums.UserStatus.ACTIVE);
      assertThat(saved.getEmail()).isEqualTo(subject + "@example.test");
    } finally {
      org.mockito.Mockito.reset(accounts);
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void simultaneousFirstLoginsWithSameIdentityReuseTheMember(boolean sameEmail) throws Exception {
    String subject = id(), email = id() + "@example.test";
    String otherEmail = sameEmail ? email : id() + "@example.test";
    var identity = identity(OAuthProvider.KAKAO, subject, email, true);
    var otherIdentity = identity(OAuthProvider.KAKAO, subject, otherEmail, true);
    var firstLookup = new CountDownLatch(1);
    var otherLoginCommitted = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    // Hold the first empty lookup until the other request has committed the same identity.
    org.mockito.Mockito.doAnswer(
            invocation -> {
              var matchingUser =
                  jdbc.query(
                          "select user_id from social_account where provider='KAKAO' and provider_user_id=?",
                          (row, index) -> row.getLong("user_id"),
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
        .when(accounts)
        .findUserByProviderAndProviderUserId(SocialProvider.KAKAO, subject);

    try (var pool = Executors.newSingleThreadExecutor()) {
      var waitingLogin = pool.submit(() -> login.login(identity, LoginTarget.SERVICE));
      try {
        assertThat(firstLookup.await(10, TimeUnit.SECONDS)).isTrue();
        var committed = login.login(otherIdentity, LoginTarget.SERVICE);
        otherLoginCommitted.countDown();
        var resumed = waitingLogin.get(10, TimeUnit.SECONDS);

        assertThat(jwt.verifyAccess(resumed.accessToken(), LoginTarget.SERVICE).userId())
            .isEqualTo(jwt.verifyAccess(committed.accessToken(), LoginTarget.SERVICE).userId());
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from users where email_key in (?, ?)",
                    Long.class,
                    email,
                    otherEmail))
            .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from social_account where provider='KAKAO' and provider_user_id=?",
                    Long.class,
                    subject))
            .isEqualTo(1L);
      } finally {
        otherLoginCommitted.countDown();
      }
    } finally {
      org.mockito.Mockito.reset(accounts);
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
