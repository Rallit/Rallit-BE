package com.rallit.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "RALLIT_ENV_FILE=.env.test-disabled",
      "SERVICE_ORIGINS=https://frontend.example.test",
      "ADMIN_ORIGINS=https://admin.example.test"
    })
@AutoConfigureMockMvc
class FrontendOriginConfigurationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;

  @Test
  void configuredFrontendOriginsAreAppliedOnlyToTheirOwnLoginTarget() throws Exception {
    mvc.perform(
            options("/api/v1/auth/refresh")
                .header("Origin", "https://frontend.example.test")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.test"));
    mvc.perform(
            options("/api/v1/admin/auth/refresh")
                .header("Origin", "https://admin.example.test")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.test"));
    mvc.perform(
            options("/api/v1/admin/auth/refresh")
                .header("Origin", "https://frontend.example.test")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/v1/auth/refresh")
                .header("Origin", "https://admin.example.test")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }
}
