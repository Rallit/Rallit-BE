package com.rallit.global.security.authentication;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class UserDetailsIntegrationTest extends IntegrationTestSupport {
  @Autowired UserDetailsService details;
  @Autowired UserRegistrationService registration;
  @Autowired TokenSessionService sessions;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  long createUser() {
    return registration.getOrCreateUser(kakao(UUID.randomUUID().toString())).getId();
  }

  @Test
  void loaderUsesStableMemberIdAndReloadsCurrentRoleAndStatus() {
    long id = createUser();
    org.springframework.security.core.userdetails.UserDetailsService standardService = details;
    var pending = standardService.loadUserByUsername(Long.toString(id));
    assertThat(pending.getUsername()).isEqualTo(Long.toString(id));
    assertThat(pending.getPassword()).isNull();
    assertThat(pending.isEnabled()).isTrue();
    assertThat(pending.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
    assertThat(((UserDetails) pending).onboardingCompleted()).isFalse();
    jdbc.update("update users set role='ADMIN',status='ACTIVE',gender='FEMALE' where id=?", id);
    var current = details.loadUserByUsername(Long.toString(id));
    assertThat(current.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
    assertThat(current.onboardingCompleted()).isTrue();
  }

  @Test
  void pendingMemberWorksAndDeletedMemberIsRejectedWithExistingAccessToken() throws Exception {
    long id = createUser();
    var access = sessions.login(id, LoginTarget.SERVICE).accessToken();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.id").value(id))
        .andExpect(jsonPath("$.result.onboardingCompleted").value(false));
    jdbc.update("delete from social_account where user_id=?", id);
    jdbc.update("delete from users where id=?", id);
    assertThatThrownBy(() -> details.loadUserByUsername(Long.toString(id)))
        .isInstanceOf(UsernameNotFoundException.class);
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401"));
  }

  @Test
  void adminDemotionIsAppliedToExistingAccessToken() throws Exception {
    long id = createUser();
    jdbc.update("update users set role='ADMIN' where id=?", id);
    var access = sessions.login(id, LoginTarget.ADMIN).accessToken();
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.role").value("ADMIN"));
    jdbc.update("update users set role='USER' where id=?", id);
    mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH403"));
  }

  @Test
  void malformedOrMissingMemberIsReportedAsUsernameNotFound() {
    for (String value :
        new String[] {
          null, "", "not-an-id", "0", "-1", "9223372036854775808", "9223372036854775807"
        }) {
      assertThatThrownBy(() -> details.loadUserByUsername(value))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }
}
