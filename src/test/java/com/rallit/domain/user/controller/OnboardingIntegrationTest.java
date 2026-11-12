package com.rallit.domain.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.model.OAuthProvider;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class OnboardingIntegrationTest extends IntegrationTestSupport {
  private static final String PATH = "/api/v1/users/me/onboarding";
  private static final String PAYLOAD = """
      {
        "nickname": "%s",
        "gender": "FEMALE",
        "ageGroup": "TWENTIES",
        "experiencePeriod": "ONE_TO_THREE_YEARS",
        "ntrp": "4.0+",
        "sameGenderOnly": false,
        "preferredMatchTypes": ["DOUBLES", "RALLY"],
        "activityAreaCodes": ["11440", "11410"],
        "preferredTimeSlots": ["WEEKEND_MORNING"]
      }
      """;

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;

  @Test
  void completesOnboardingAndHandlesRepeatedSubmissions() throws Exception {
    var member = login("환영 인사 이름");
    var body = PAYLOAD.formatted(member.nickname());
    mvc.perform(get("/api/v1/users/me").header("Authorization", member.bearer()))
        .andExpect(jsonPath("$.result.profile").value(nullValue()));

    submit(member, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.result.onboardingCompleted").value(true))
        .andExpect(jsonPath("$.result.socialDisplayName").value("환영 인사 이름"))
        .andExpect(jsonPath("$.result.profile.ntrp").value("4.0+"))
        .andExpect(jsonPath("$.result.profile.sameGenderOnly").value(false))
        .andExpect(jsonPath("$.result.profile.activityAreaCodes[0]").value("11410"));
    submit(member, body.replace("\"11440\", \"11410\"", "\"11410\", \"11440\", \"11440\""))
        .andExpect(status().isOk());
    submit(member, body.replace("WEEKEND_MORNING", "WEEKEND_AFTERNOON"))
        .andExpect(status().isConflict());

    mvc.perform(get("/api/v1/users/me").header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.socialDisplayName").value("환영 인사 이름"))
        .andExpect(jsonPath("$.result.profile.ntrp").value("4.0+"));
  }

  @Test
  void rejectsInvalidInputsWithoutCompletingOnboarding() throws Exception {
    var member = login();
    var body = PAYLOAD.formatted(member.nickname());
    submit(member, body.replace("\"sameGenderOnly\": false,", ""))
        .andExpect(status().isBadRequest());
    submit(member, body.replace("4.0+", "4.0")).andExpect(status().isBadRequest());
    submit(member, body.replace("11440", "99999")).andExpect(status().isBadRequest());
    submit(member, body.replace("\"11440\", \"11410\"", "\"11440\", \"11410\", \"11680\", \"11110\""))
        .andExpect(status().isBadRequest());
    submit(member, body.replace("false", "true").replace("DOUBLES", "MIXED_DOUBLES"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/users/me").header("Authorization", member.bearer()))
        .andExpect(jsonPath("$.result.onboardingCompleted").value(false));
  }

  @Test
  void rejectsCoercedJsonScalars() throws Exception {
    var member = login();
    var body = PAYLOAD.formatted(member.nickname());
    submit(member, body.replace("\"FEMALE\"", "1")).andExpect(status().isBadRequest());
    submit(member, body.replace("false", "0")).andExpect(status().isBadRequest());
    submit(member, body.replace("\"11440\"", "11440")).andExpect(status().isBadRequest());
    submit(member, body.replace("\"4.0+\"", "3.0")).andExpect(status().isBadRequest());
  }

  @Test
  void nicknameAvailabilityAndFinalSubmissionRespectTheSameUniquenessRule() throws Exception {
    var member = login();
    var other = login();
    var candidate = other.nickname().toUpperCase(java.util.Locale.ROOT);
    mvc.perform(get("/api/v1/users/nickname-availability").param("nickname", member.nickname())
            .header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.available").value(true));
    mvc.perform(get("/api/v1/users/nickname-availability").param("nickname", candidate)
            .header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.available").value(false));
    submit(member, PAYLOAD.formatted(candidate)).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/users/me").header("Authorization", member.bearer()))
        .andExpect(jsonPath("$.result.onboardingCompleted").value(false));
  }

  private ResultActions submit(Member member, String body) throws Exception {
    return mvc.perform(put(PATH).header("Authorization", member.bearer())
        .contentType("application/json").content(body));
  }

  private Member login() {
    return login(null);
  }

  private Member login(String displayName) {
    String subject = UUID.randomUUID().toString();
    var user = users.getOrCreateUser(
        new SocialIdentity(OAuthProvider.KAKAO, subject, subject + "@example.test", true, displayName));
    return new Member(user.getNickname(),
        "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken());
  }

  private record Member(String nickname, String bearer) {}
}
