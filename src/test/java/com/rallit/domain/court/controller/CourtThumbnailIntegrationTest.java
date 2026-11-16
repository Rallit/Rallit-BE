package com.rallit.domain.court.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.court-thumbnail.storage-path=./build/court-thumbnail-integration"
})
@AutoConfigureMockMvc
@Transactional
class CourtThumbnailIntegrationTest extends IntegrationTestSupport {
  private static final String PATH = "/api/v1/courts/{courtId}/thumbnail";

  @Autowired MockMvc mvc;
  @Autowired EntityManager entities;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenSessionService sessions;

  @Test
  void servesTheSeededCourtPngToAnOnboardedMember() throws Exception {
    long courtId = court();
    String key = UUID.randomUUID() + ".png";
    Path path = Path.of("build/court-thumbnail-integration", key);
    Files.createDirectories(path.getParent());
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
    try {
      setKey(courtId, key);
      mvc.perform(get(PATH, courtId).header("Authorization", activeMember()))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/png"))
          .andExpect(content().bytes(Files.readAllBytes(path)));
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void requiresAuthenticationAndOnboardingBeforeCheckingTheCourt() throws Exception {
    long courtId = court();
    mvc.perform(get(PATH, courtId)).andExpect(status().isUnauthorized());
    mvc.perform(get(PATH, courtId).header("Authorization", bearer(User.signUp())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COURT_ONBOARDING_REQUIRED"));
  }

  @Test
  void treatsAnAbsentCourtKeyOrObjectAsNotFound() throws Exception {
    String auth = activeMember();
    long courtId = court();
    mvc.perform(get(PATH, Long.MAX_VALUE).header("Authorization", auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COURT_THUMBNAIL_NOT_FOUND"));
    mvc.perform(get(PATH, courtId).header("Authorization", auth))
        .andExpect(status().isNotFound());
    setKey(courtId, UUID.randomUUID() + ".jpg");
    mvc.perform(get(PATH, courtId).header("Authorization", auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COURT_THUMBNAIL_NOT_FOUND"));
  }

  @Test
  void reportsAFailedStorageReadAsServiceUnavailable() throws Exception {
    long courtId = court();
    setKey(courtId, "../invalid.jpg");
    mvc.perform(get(PATH, courtId).header("Authorization", activeMember()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COMMON503"));
  }

  private long court() {
    var court = EntityFixtures.court();
    entities.persist(court);
    entities.flush();
    return court.getId();
  }

  private void setKey(long courtId, String key) {
    jdbc.update("update court set thumbnail_image_key = ? where id = ?", key, courtId);
    entities.clear();
  }

  private String activeMember() {
    return bearer(EntityFixtures.user(entities, "코트" + UUID.randomUUID().toString().substring(0, 8),
        Gender.FEMALE));
  }

  private String bearer(User member) {
    entities.persist(member);
    entities.flush();
    return "Bearer " + sessions.login(member.getId(), LoginTarget.SERVICE).accessToken();
  }
}
