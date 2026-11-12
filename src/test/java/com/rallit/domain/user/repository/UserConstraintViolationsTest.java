package com.rallit.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UserConstraintViolationsTest {
  @Test
  void recognizesStructuredNicknameConstraintEvenWithoutReadableMessage() {
    var cause =
        new ConstraintViolationException(
            null, new SQLException(null, "23505"), "uk_users_nickname");
    assertThat(
            UserConstraintViolations.isNicknameConflict(
                new DataIntegrityViolationException("wrapped", cause)))
        .isTrue();
  }

  @Test
  void doesNotInferConstraintFromArbitraryMessageOrOtherConstraint() {
    assertThat(
            UserConstraintViolations.isNicknameConflict(
                new DataIntegrityViolationException("uk_users_nickname")))
        .isFalse();
    var other =
        new ConstraintViolationException(
            "uk_users_nickname", new SQLException("duplicate", "23505"), "uk_users_provider");
    assertThat(UserConstraintViolations.isNicknameConflict(other)).isFalse();
  }
}
