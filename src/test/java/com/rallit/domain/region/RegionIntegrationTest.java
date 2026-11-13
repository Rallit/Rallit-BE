package com.rallit.domain.region;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class RegionIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;

  @Test
  void listsCitiesAndTheirAreasForOnboarding() throws Exception {
    var user = users.getOrCreateUser(kakao(UUID.randomUUID().toString()));
    var token = "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
    mvc.perform(get("/api/v1/cities").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(1))
        .andExpect(jsonPath("$.result[0].code").value("11"));
    mvc.perform(get("/api/v1/cities/11/areas").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(25))
        .andExpect(jsonPath("$.result[?(@.code == '11440')].name").value("마포구"));
    mvc.perform(get("/api/v1/cities/27/areas").header("Authorization", token))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/cities")).andExpect(status().isUnauthorized());
  }
}
