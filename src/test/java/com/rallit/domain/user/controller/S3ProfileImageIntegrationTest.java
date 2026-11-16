package com.rallit.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.support.IntegrationTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SpringBootTest(properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.image-storage.backend=s3",
    "rallit.image-storage.s3.bucket=image-test",
    "rallit.image-storage.s3.region=ap-northeast-2"
})
@AutoConfigureMockMvc
class S3ProfileImageIntegrationTest extends IntegrationTestSupport {
  private static final String WRITE_PATH = "/api/v1/users/me/profile-image";

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService registration;
  @Autowired UserRepository users;
  @Autowired TokenSessionService sessions;
  @MockitoBean S3Client client;

  @Test
  void uploadsNormalizedBytesWithTheirTypeAndLengthAndUsesTheSameObjectForReadAndDelete()
      throws Exception {
    var member = uploadProfile();
    var request = ArgumentCaptor.forClass(PutObjectRequest.class);
    var body = ArgumentCaptor.forClass(RequestBody.class);
    verify(client).putObject(request.capture(), body.capture());
    assertThat(request.getValue().bucket()).isEqualTo("image-test");
    assertThat(request.getValue().key()).isEqualTo(member.objectKey());
    assertThat(request.getValue().contentType()).isEqualTo("image/png");
    byte[] bytes;
    try (var stream = body.getValue().contentStreamProvider().newStream()) {
      bytes = stream.readAllBytes();
    }
    assertThat(body.getValue().optionalContentLength()).contains((long) bytes.length);
    assertThat(ImageIO.read(new ByteArrayInputStream(bytes)).getWidth()).isEqualTo(2);

    when(client.getObject(member.getRequest())).thenReturn(
        new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes)));
    mvc.perform(get(member.url()).header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(bytes));

    mvc.perform(delete(WRITE_PATH).header("Authorization", member.bearer()))
        .andExpect(status().isNoContent());
    verify(client).deleteObject(DeleteObjectRequest.builder()
        .bucket("image-test").key(member.objectKey()).build());
  }

  @Test
  void distinguishesAMissingObjectFromAnS3AccessFailure() throws Exception {
    var member = uploadProfile();
    when(client.getObject(member.getRequest()))
        .thenThrow(NoSuchKeyException.builder().statusCode(404).build())
        .thenThrow(S3Exception.builder().statusCode(403).build())
        .thenThrow(NoSuchBucketException.builder().statusCode(404).build());

    mvc.perform(get(member.url()).header("Authorization", member.bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(get(member.url()).header("Authorization", member.bearer()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COMMON503"));
    mvc.perform(get(member.url()).header("Authorization", member.bearer()))
        .andExpect(status().isServiceUnavailable());
  }

  private Member uploadProfile() throws Exception {
    var user = registration.getOrCreateUser(kakao(UUID.randomUUID().toString()));
    String bearer = "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
    String url = "/api/v1/users/" + user.getId() + "/profile-image";
    var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
    mvc.perform(multipart(HttpMethod.PUT, WRITE_PATH)
            .file(new MockMultipartFile("file", "profile.png", "image/png", image.toByteArray()))
            .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    var key = users.findById(user.getId()).orElseThrow().getProfileImageKey();
    assertThat(key).matches("[0-9a-f-]+\\.png");
    return new Member(url, bearer, "profile-images/" + key);
  }

  private record Member(String url, String bearer, String objectKey) {
    GetObjectRequest getRequest() {
      return GetObjectRequest.builder().bucket("image-test").key(objectKey).build();
    }
  }
}
