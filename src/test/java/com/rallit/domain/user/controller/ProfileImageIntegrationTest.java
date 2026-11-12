package com.rallit.domain.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.profile-image.storage-path=./build/profile-image-integration"
})
@AutoConfigureMockMvc
class ProfileImageIntegrationTest extends IntegrationTestSupport {
  private static final String PATH = "/api/v1/users/me/profile-image";

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;

  @Test
  void uploadsServesAndDeletesAnImageDuringOnboarding() throws Exception {
    var owner = login();
    var viewer = login();
    var bytes = image("png");
    var url = "/api/v1/users/" + owner.id() + "/profile-image";

    upload(owner, "avatar.png", "image/png", bytes)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    mvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer()))
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).header("Authorization", viewer.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(bytes));

    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer()))
        .andExpect(jsonPath("$.result.profileImageUrl").value(nullValue()));
    mvc.perform(get(url).header("Authorization", viewer.bearer())).andExpect(status().isNotFound());
  }

  @Test
  void rejectsOversizedOrDisguisedUploadsAndKeepsTheExistingImage() throws Exception {
    var owner = login();
    var original = image("jpeg");
    upload(owner, "avatar.jpeg", "image/jpeg", original).andExpect(status().isOk());
    upload(owner, "avatar.png", "image/png", original).andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", Arrays.copyOf(image("png"), 33))
        .andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", new byte[5 * 1024 * 1024 + 1])
        .andExpect(status().is(413));
    mvc.perform(get("/api/v1/users/" + owner.id() + "/profile-image")
            .header("Authorization", owner.bearer()))
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(content().bytes(original));
    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
  }

  private ResultActions upload(Member member, String filename, String type, byte[] bytes)
      throws Exception {
    return mvc.perform(multipart(HttpMethod.PUT, PATH)
        .file(new MockMultipartFile("file", filename, type, bytes))
        .header("Authorization", member.bearer()));
  }

  private Member login() {
    var user = users.getOrCreateUser(kakao(UUID.randomUUID().toString()));
    return new Member(user.getId(),
        "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken());
  }

  private byte[] image(String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  private record Member(long id, String bearer) {}
}
