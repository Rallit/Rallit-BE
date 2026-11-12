package com.rallit.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles({"temp", "prod"})
@AutoConfigureMockMvc
class ProductionDocsIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;

  @Test
  void prodBlocksSwaggerEvenWhenTempIsActive() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }
}
