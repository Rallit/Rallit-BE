package com.rallit.global.config;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class LocalDocsIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;

  @Test
  void localSwaggerDocumentsSocialLoginAndSeparatesAdminOperations() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("인증", "회원", "매치 참가 신청", "운영자")))
        .andExpect(jsonPath("$.paths['/api/v1/auth/kakao'].get.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/kakao'].get.responses['302']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/google'].get.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/google'].get.responses['302']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.tags[0]").value("회원"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/auth/refresh'].post.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/me'].get.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/me'].get.security[0].bearerAuth").exists());
  }
}
