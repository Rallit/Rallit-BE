package com.rallit.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.rallit.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
class NicknameCollisionIntegrationTest extends IntegrationTestSupport {
  @Autowired UserRegistrationService registration;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean NicknameGenerator generator;

  @Test
  void duplicateNicknameRetriesWithFreshTransaction() {
    String duplicate = "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String unique = "U" + duplicate;
    jdbc.update(
        "insert into users(provider,provider_user_id,nickname,role,status,created_at,updated_at)"
            + " values('KAKAO',?,?,'USER','PENDING',now(),now())",
        UUID.randomUUID().toString(),
        duplicate);
    when(generator.generate()).thenReturn(duplicate, unique);
    assertThat(registration.getOrCreateKakaoUser(UUID.randomUUID().toString()).getNickname())
        .isEqualTo(unique);
  }
}
