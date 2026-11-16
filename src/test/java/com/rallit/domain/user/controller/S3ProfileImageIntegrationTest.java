package com.rallit.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.zaxxer.hikari.HikariDataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
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
  private static final DatabaseState RELEASED = new DatabaseState(false, 0);

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService registration;
  @MockitoSpyBean UserRepository users;
  @Autowired TokenSessionService sessions;
  @Autowired DataSource dataSource;
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

    var retrieval = new AtomicReference<DatabaseState>();
    when(client.getObject(member.getRequest())).thenAnswer(invocation -> {
      retrieval.set(databaseState());
      return new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes));
    });
    mvc.perform(get(member.url()).header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(bytes));
    assertThat(retrieval.get()).isEqualTo(RELEASED);

    var deletion = captureDatabaseStateOnObjectDelete();
    mvc.perform(delete(WRITE_PATH).header("Authorization", member.bearer()))
        .andExpect(status().isNoContent());
    verify(client).deleteObject(deleteRequest(member.objectKey()));
    assertThat(deletion.get()).isEqualTo(RELEASED);
    assertThat(users.findById(member.id()).orElseThrow().getProfileImageKey()).isNull();
  }

  @Test
  void cleansUpThePreviousObjectAfterTheReplacementTransactionReturns() throws Exception {
    var member = uploadProfile();
    var deletion = captureDatabaseStateOnObjectDelete();

    upload(member.bearer()).andExpect(status().isOk());

    verify(client).deleteObject(deleteRequest(member.objectKey()));
    assertThat(deletion.get()).isEqualTo(RELEASED);
    assertThat(profileObjectKey(member.id())).isNotEqualTo(member.objectKey());
  }

  @Test
  void cleansUpOnlyTheNewObjectAfterTheReplacementRollsBack() throws Exception {
    var member = uploadProfile();
    var deletion = captureDatabaseStateOnObjectDelete();
    doReturn(Optional.empty()).when(users).findByIdForUpdate(member.id());

    upload(member.bearer()).andExpect(status().isUnauthorized());

    var uploads = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(client, times(2)).putObject(uploads.capture(), any(RequestBody.class));
    String newObjectKey = uploads.getAllValues().getLast().key();
    verify(client).deleteObject(deleteRequest(newObjectKey));
    verify(client, never()).deleteObject(deleteRequest(member.objectKey()));
    assertThat(deletion.get()).isEqualTo(RELEASED);
    assertThat(profileObjectKey(member.id())).isEqualTo(member.objectKey());
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
    upload(bearer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    return new Member(user.getId(), url, bearer, profileObjectKey(user.getId()));
  }

  private ResultActions upload(String bearer) throws Exception {
    var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
    return mvc.perform(multipart(HttpMethod.PUT, WRITE_PATH)
            .file(new MockMultipartFile("file", "profile.png", "image/png", image.toByteArray()))
            .header("Authorization", bearer));
  }

  private String profileObjectKey(long userId) {
    var key = users.findById(userId).orElseThrow().getProfileImageKey();
    assertThat(key).matches("[0-9a-f-]+\\.png");
    return "profile-images/" + key;
  }

  private AtomicReference<DatabaseState> captureDatabaseStateOnObjectDelete() {
    var deletion = new AtomicReference<DatabaseState>();
    when(client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
      deletion.set(databaseState());
      return DeleteObjectResponse.builder().build();
    });
    return deletion;
  }

  private DatabaseState databaseState() throws SQLException {
    return new DatabaseState(TransactionSynchronizationManager.isActualTransactionActive(),
        dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
  }

  private DeleteObjectRequest deleteRequest(String objectKey) {
    return DeleteObjectRequest.builder().bucket("image-test").key(objectKey).build();
  }

  private record Member(long id, String url, String bearer, String objectKey) {
    GetObjectRequest getRequest() {
      return GetObjectRequest.builder().bucket("image-test").key(objectKey).build();
    }
  }

  private record DatabaseState(boolean transactionActive, int activeConnections) {}
}
