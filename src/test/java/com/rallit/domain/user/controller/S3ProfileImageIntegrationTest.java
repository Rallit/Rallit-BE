package com.rallit.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.ProfileImageNormalizer;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.global.storage.PresignedImageStorage;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.image-storage.backend=s3",
    "rallit.image-storage.s3.bucket=image-test",
    "rallit.image-storage.s3.region=ap-northeast-2"
})
@AutoConfigureMockMvc
class S3ProfileImageIntegrationTest extends IntegrationTestSupport {
  private static final String WRITE_PATH = "/api/v1/users/me/profile-image";
  private static final String UPLOAD_PATH = WRITE_PATH + "/uploads";
  private static final String UPLOAD_URL = "https://image-test.s3.example.test/";
  private static final String ADMIN_ORIGIN = "https://admin.rallit.co.kr";
  private static final int MAX_BYTES = ProfileImageNormalizer.MAX_BYTES;

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService registration;
  @Autowired TokenSessionService sessions;
  @Autowired CourtRepository courts;
  @Autowired Clock clock;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired TransactionTemplate transactions;
  @Autowired DataSource dataSource;
  @MockitoSpyBean UserRepository users;
  @MockitoBean S3Client client;
  @MockitoBean PresignedImageStorage presigned;

  @BeforeEach
  void fakeSignedRequests() throws Exception {
    when(presigned.signUpload(anyString(), any(MediaType.class), eq(Duration.ofMinutes(5)), eq((long) MAX_BYTES)))
        .thenAnswer(call -> new PresignedImageStorage.SignedUpload(UPLOAD_URL,
            Map.of("key", call.getArgument(0)), clock.instant().plusSeconds(300)));
    when(client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(call -> {
      assertConnectionReleased();
      return DeleteObjectResponse.builder().build();
    });
  }

  @Test
  void finalizesTheDirectUploadAndIssuesASignedReadWithoutProxyingBytes() throws Exception {
    var user = member(LoginTarget.SERVICE);
    byte[] original = ProfileImageIntegrationTest.withPngExif(png(48, 24),
        ProfileImageIntegrationTest.rotatedExifWithGps());
    var upload = issue(UPLOAD_PATH, user, original);
    assertThat(users.findById(user.id()).orElseThrow().getProfileImageKey()).isNull();
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

    stage(upload, original);
    complete(UPLOAD_PATH, upload, user)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.profileImageUrl").value(user.profilePath()));

    String key = profileKey(user);
    var put = ArgumentCaptor.forClass(PutObjectRequest.class);
    var body = ArgumentCaptor.forClass(RequestBody.class);
    verify(client).putObject(put.capture(), body.capture());
    assertThat(put.getValue().bucket()).isEqualTo("image-test");
    assertThat(put.getValue().key()).isEqualTo("profile-images/" + key);
    assertThat(put.getValue().contentType()).isEqualTo("image/png");
    byte[] stored;
    try (var stream = body.getValue().contentStreamProvider().newStream()) {
      stored = stream.readAllBytes();
    }
    assertThat(body.getValue().optionalContentLength()).contains((long) stored.length);
    assertThat(ImageIO.read(new ByteArrayInputStream(stored)).getWidth()).isEqualTo(24);
    assertThat(ImageMetadataReader.readMetadata(new ByteArrayInputStream(stored))
        .getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    verify(presigned).deleteUpload(upload.stagingKey());

    var read = signRead("profile-images/", key);
    mvc.perform(get(user.profilePath() + "/url").header("Authorization", user.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.url").value(read.url()))
        .andExpect(jsonPath("$.result.expiresAt").value(read.expiresAt().toString()));
    mvc.perform(get(user.profilePath()).header("Authorization", user.bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(multipart(HttpMethod.PUT, WRITE_PATH)
            .file(new MockMultipartFile("file", "old.png", "image/png", original))
            .header("Authorization", user.bearer()))
        .andExpect(status().isNotFound());
    verify(client, never()).getObject(any(GetObjectRequest.class));

    mvc.perform(delete(WRITE_PATH).header("Authorization", user.bearer()))
        .andExpect(status().isNoContent());
    verify(client).deleteObject(deleteRequest("profile-images/" + key));
    mvc.perform(get(user.profilePath() + "/url").header("Authorization", user.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsInvalidBytesAndExcessiveDimensionsThroughThePresignedFlow() throws Exception {
    var user = member(LoginTarget.SERVICE);
    for (byte[] bad : new byte[][] { {1, 2, 3}, png(4097, 2) }) {
      var upload = issue(UPLOAD_PATH, user, bad);
      stage(upload, bad);
      complete(UPLOAD_PATH, upload, user).andExpect(status().isBadRequest());
    }
    assertThat(users.findById(user.id()).orElseThrow().getProfileImageKey()).isNull();
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void appliesSizeLimitsAtIssuanceAndAtCompletion() throws Exception {
    var user = member(LoginTarget.SERVICE);
    initiate(UPLOAD_PATH, user, MAX_BYTES + 1L).andExpect(status().is(413));
    var upload = issue(UPLOAD_PATH, user, png(2, 2));
    when(presigned.readUpload(upload.stagingKey(), MAX_BYTES))
        .thenThrow(new PresignedImageStorage.UploadTooLargeException());
    complete(UPLOAD_PATH, upload, user).andExpect(status().is(413));
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void enforcesTicketOwnershipAndHandlesMissingOrExpiredUploads() throws Exception {
    var user = member(LoginTarget.SERVICE);
    var other = member(LoginTarget.SERVICE);
    byte[] png = png(2, 2);
    var upload = issue(UPLOAD_PATH, user, png);
    complete(UPLOAD_PATH, upload, other).andExpect(status().isNotFound());
    complete(UPLOAD_PATH, upload, user).andExpect(status().isConflict());
    jdbc.update("update image_upload set expires_at = current_timestamp - interval '1 second' where id = ?",
        upload.id());
    complete(UPLOAD_PATH, upload, user).andExpect(status().isGone());
    verify(presigned).readUpload(upload.stagingKey(), MAX_BYTES);
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void replacesAfterCommitAndDoesNotRestoreTheOldKeyWhenCompletionIsRetried() throws Exception {
    var user = member(LoginTarget.SERVICE);
    byte[] png = png(2, 2);
    var first = saveProfile(user, png);
    String previous = profileKey(user);
    saveProfile(user, png);
    String current = profileKey(user);
    assertThat(current).isNotEqualTo(previous);
    verify(client).deleteObject(deleteRequest("profile-images/" + previous));

    complete(UPLOAD_PATH, first, user).andExpect(status().isOk());
    assertThat(profileKey(user)).isEqualTo(current);
    verify(client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(presigned).readUpload(first.stagingKey(), MAX_BYTES);
  }

  @Test
  void deletesOnlyTheNewObjectWhenTheDatabaseUpdateRollsBack() throws Exception {
    var user = member(LoginTarget.SERVICE);
    byte[] png = png(2, 2);
    saveProfile(user, png);
    String previous = "profile-images/" + profileKey(user);

    var second = issue(UPLOAD_PATH, user, png);
    stage(second, png);
    doReturn(Optional.empty()).when(users).findByIdForUpdate(user.id());
    complete(UPLOAD_PATH, second, user).andExpect(status().isUnauthorized());

    var uploads = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(client, times(2)).putObject(uploads.capture(), any(RequestBody.class));
    verify(client).deleteObject(deleteRequest(uploads.getAllValues().getLast().key()));
    verify(client, never()).deleteObject(deleteRequest(previous));
    assertThat("profile-images/" + profileKey(user)).isEqualTo(previous);
  }

  @Test
  void restrictsCourtUploadsToAdminButAllowsOnboardedMembersToGetASignedThumbnail() throws Exception {
    var admin = member(LoginTarget.ADMIN);
    var waiting = member(LoginTarget.SERVICE);
    var viewerId = transactions.execute(status -> {
      var user = EntityFixtures.user(entities, "사진" + UUID.randomUUID().toString().substring(0, 8), Gender.FEMALE);
      entities.persist(user);
      return user.getId();
    });
    String viewer = bearer(viewerId, LoginTarget.SERVICE);
    long court = courts.saveAndFlush(EntityFixtures.court()).getId();
    long otherCourt = courts.saveAndFlush(EntityFixtures.court()).getId();
    String readPath = "/api/v1/courts/" + court + "/thumbnail";
    String writePath = "/api/v1/admin/courts/" + court + "/thumbnail/uploads";
    byte[] png = png(2, 2);
    mvc.perform(post(writePath)).andExpect(status().isForbidden());
    mvc.perform(post(writePath).header("Origin", ADMIN_ORIGIN)).andExpect(status().isUnauthorized());
    initiate(writePath, waiting, png.length).andExpect(status().isUnauthorized());
    var upload = issue(writePath, admin, png);
    stage(upload, png);
    complete("/api/v1/admin/courts/" + otherCourt + "/thumbnail/uploads", upload, admin)
        .andExpect(status().isNotFound());
    complete(writePath, upload, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.thumbnailUrl").value(readPath));

    String key = courts.findById(court).orElseThrow().getThumbnailImageKey();
    verify(client).putObject(org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(
        request -> request.key().equals("court-thumbnails/" + key)), any(RequestBody.class));
    var signed = signRead("court-thumbnails/", key);
    mvc.perform(get(readPath + "/url").header("Authorization", waiting.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(readPath + "/url").header("Authorization", viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.url").value(signed.url()));
    verify(client, never()).getObject(any(GetObjectRequest.class));
  }

  private Member member(LoginTarget target) {
    long userId = registration.getOrCreateUser(kakao(UUID.randomUUID().toString())).getId();
    if (target == LoginTarget.ADMIN) jdbc.update("update users set role='ADMIN' where id=?", userId);
    return new Member(userId, bearer(userId, target));
  }

  private String bearer(long userId, LoginTarget target) {
    return "Bearer " + sessions.login(userId, target).accessToken();
  }

  private ResultActions initiate(String path, Member user, long size) throws Exception {
    return mvc.perform(post(path).header("Authorization", user.bearer())
        .header("Origin", originFor(path))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("fileName", "photo.png", "contentType", "image/png", "size", size))));
  }

  private Upload issue(String path, Member user, byte[] png) throws Exception {
    var response = initiate(path, user, png.length)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.mode").value("S3"))
        .andExpect(jsonPath("$.result.url").value(UPLOAD_URL))
        .andReturn().getResponse();
    var result = json.readTree(response.getContentAsString()).path("result");
    var upload = new Upload(UUID.fromString(result.path("uploadId").asText()));
    assertThat(result.path("fields").path("key").asText()).isEqualTo(upload.stagingKey());
    return upload;
  }

  private void stage(Upload upload, byte[] png) throws IOException {
    when(presigned.readUpload(upload.stagingKey(), MAX_BYTES))
        .thenReturn(Optional.of(new PresignedImageStorage.StagedImage(png, "image/png")));
  }

  private Upload saveProfile(Member user, byte[] bytes) throws Exception {
    var upload = issue(UPLOAD_PATH, user, bytes);
    stage(upload, bytes);
    complete(UPLOAD_PATH, upload, user).andExpect(status().isOk());
    return upload;
  }

  private ResultActions complete(String path, Upload upload, Member user) throws Exception {
    return mvc.perform(post(path + "/{id}/complete", upload.id())
        .header("Authorization", user.bearer()).header("Origin", originFor(path)));
  }

  private static String originFor(String path) {
    return path.startsWith("/api/v1/admin/") ? ADMIN_ORIGIN : "https://rallit.co.kr";
  }

  private String profileKey(Member user) {
    var key = users.findById(user.id()).orElseThrow().getProfileImageKey();
    assertThat(key).matches("[0-9a-f-]+\\.png");
    return key;
  }

  private PresignedImageStorage.SignedRead signRead(String prefix, String key) throws IOException {
    var signed = new PresignedImageStorage.SignedRead(UPLOAD_URL + prefix + key + "?X-Amz-Signature=test",
        clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(300));
    when(presigned.signRead(prefix, key, Duration.ofMinutes(5))).thenAnswer(call -> {
      assertConnectionReleased();
      return signed;
    });
    return signed;
  }

  private void assertConnectionReleased() throws java.sql.SQLException {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
  }

  private static DeleteObjectRequest deleteRequest(String key) {
    return DeleteObjectRequest.builder().bucket("image-test").key(key).build();
  }

  private static byte[] png(int width, int height) throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", out);
    return out.toByteArray();
  }

  private record Member(long id, String bearer) {
    String profilePath() { return "/api/v1/users/" + id + "/profile-image"; }
  }

  private record Upload(UUID id) {
    String stagingKey() { return "image-uploads/" + id; }
  }
}
