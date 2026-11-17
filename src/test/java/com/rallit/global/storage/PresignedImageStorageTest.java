package com.rallit.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.json.JsonMapper;

class PresignedImageStorageTest {
  private static final String BUCKET = "image-test";
  private static final String UUID = "87654321-1111-2222-3333-123456789abc";
  private static final String STAGING_KEY = "image-uploads/" + UUID;
  private static final Region REGION = Region.AP_NORTHEAST_2;
  private static final Instant NOW = Instant.parse("2026-11-17T12:34:56Z");
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final StaticCredentialsProvider SESSION_CREDENTIALS = StaticCredentialsProvider.create(
      AwsSessionCredentials.create("example-access", "example-secret", "example-session-token"));
  private static final GetObjectRequest STAGED_REQUEST =
      GetObjectRequest.builder().bucket(BUCKET).key(STAGING_KEY).build();

  private final S3Client client = mock(S3Client.class);
  private final S3Presigner presigner = S3Presigner.builder()
      .region(REGION)
      .credentialsProvider(SESSION_CREDENTIALS)
      .build();
  private final JsonMapper json = JsonMapper.builder().build();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final PresignedImageStorage storage =
      new PresignedImageStorage(client, presigner, SESSION_CREDENTIALS, json, clock, BUCKET, REGION.id(), "");

  @AfterEach
  void closePresigner() {
    presigner.close();
  }

  @Test
  void signsAnExactUploadPolicyWithSizeLimitAndTemporaryCredentials() throws IOException {
    var upload = storage.signUpload(STAGING_KEY, MediaType.IMAGE_PNG, TTL, 5_242_880);

    assertThat(URI.create(upload.url()).getHost()).isEqualTo("image-test.s3.ap-northeast-2.amazonaws.com");
    assertThat(URI.create(upload.url()).getScheme()).isEqualTo("https");
    assertThat(upload.expiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(upload.fields()).containsEntry("key", STAGING_KEY)
        .containsEntry("Content-Type", "image/png")
        .containsEntry("x-amz-algorithm", "AWS4-HMAC-SHA256")
        .containsEntry("x-amz-credential", "example-access/20261117/ap-northeast-2/s3/aws4_request")
        .containsEntry("x-amz-date", "20261117T123456Z")
        .containsEntry("x-amz-security-token", "example-session-token")
        .containsEntry("x-amz-signature", "4020729159edbce1a75811edc47de6b0b8d0dea955559622e0737d0b976d1d29")
        .doesNotContainKeys("acl", "success_action_redirect");
    var policy = json.readTree(Base64.getDecoder().decode(upload.fields().get("policy")));
    assertThat(policy.get("expiration").asString()).isEqualTo(upload.expiresAt().toString());
    assertThat(policy.get("conditions")).containsExactlyInAnyOrder(
        json.readTree("{\"bucket\":\"image-test\"}"),
        json.readTree("{\"key\":\"" + STAGING_KEY + "\"}"),
        json.readTree("{\"Content-Type\":\"image/png\"}"),
        json.readTree("{\"x-amz-algorithm\":\"AWS4-HMAC-SHA256\"}"),
        json.readTree("{\"x-amz-credential\":\"example-access/20261117/ap-northeast-2/s3/aws4_request\"}"),
        json.readTree("{\"x-amz-date\":\"20261117T123456Z\"}"),
        json.readTree("{\"x-amz-security-token\":\"example-session-token\"}"),
        json.readTree("[\"content-length-range\",1,5242880]"));
  }

  @Test
  void usesPathStyleForAnEndpointOverrideAndOmitsTheTokenForBasicCredentials() throws IOException {
    var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("example-access", "example-secret"));
    var endpoint = URI.create("http://localhost:9000");
    try (var localPresigner = S3Presigner.builder()
        .region(REGION)
        .credentialsProvider(credentials)
        .endpointOverride(endpoint)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()) {
      var localStorage = new PresignedImageStorage(
          client, localPresigner, credentials, json, clock, BUCKET, REGION.id(), endpoint.toString());

      var upload = localStorage.signUpload(STAGING_KEY, MediaType.IMAGE_JPEG, TTL, 100);
      var read = localStorage.signRead("court-thumbnails/", UUID + ".jpg", TTL);

      assertThat(upload.url()).isEqualTo("http://localhost:9000/image-test");
      assertThat(upload.fields()).doesNotContainKey("x-amz-security-token");
      assertThat(read.url()).startsWith("http://localhost:9000/image-test/court-thumbnails/" + UUID + ".jpg?");
    }
  }

  @Test
  void signsABrowserReadForTheNamespacedFinalKey() throws IOException {
    var read = storage.signRead("profile-images/", UUID + ".png", TTL);
    var url = URI.create(read.url());

    assertThat(url.getScheme()).isEqualTo("https");
    assertThat(url.getHost()).isEqualTo("image-test.s3.ap-northeast-2.amazonaws.com");
    assertThat(url.getPath()).isEqualTo("/profile-images/" + UUID + ".png");
    assertThat(url.getRawQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=300", "X-Amz-Security-Token=");
    assertThat(read.expiresAt()).isBetween(Instant.now().plus(TTL).minusSeconds(10), Instant.now().plus(TTL));
    assertThatThrownBy(() -> storage.signRead("profile-images/", "../other.png", TTL))
        .isInstanceOf(IOException.class);
  }

  @Test
  void readsTheStagedBytesAndContentTypeIncludingAtTheLimit() throws IOException {
    byte[] expected = {1, 2, 3};
    when(client.getObject(STAGED_REQUEST)).thenReturn(stagedResponse(3L, expected, new AtomicBoolean()));

    var image = storage.readUpload(STAGING_KEY, 3).orElseThrow();

    assertThat(image.contents()).containsExactly(expected);
    assertThat(image.contentType()).isEqualTo("image/png");
  }

  @Test
  void abortsAnOversizedResponseFromEitherTheMetadataOrTheBoundedRead() {
    var abortFromLength = new AtomicBoolean();
    var abortFromBody = new AtomicBoolean();
    when(client.getObject(STAGED_REQUEST))
        .thenReturn(stagedResponse(4L, new byte[0], abortFromLength))
        .thenReturn(stagedResponse(null, new byte[4], abortFromBody));

    assertThatThrownBy(() -> storage.readUpload(STAGING_KEY, 3))
        .isInstanceOf(PresignedImageStorage.UploadTooLargeException.class);
    assertThatThrownBy(() -> storage.readUpload(STAGING_KEY, 3))
        .isInstanceOf(PresignedImageStorage.UploadTooLargeException.class);
    assertThat(abortFromLength).isTrue();
    assertThat(abortFromBody).isTrue();
  }

  @Test
  void distinguishesAMissingObjectFromS3AccessFailures() throws IOException {
    var denied = S3Exception.builder().statusCode(403).build();
    when(client.getObject(STAGED_REQUEST))
        .thenThrow(NoSuchKeyException.builder().build())
        .thenThrow(denied);

    assertThat(storage.readUpload(STAGING_KEY, 10)).isEmpty();
    assertThatThrownBy(() -> storage.readUpload(STAGING_KEY, 10))
        .isInstanceOf(IOException.class)
        .hasCause(denied);
  }

  @Test
  void deletesOnlyAStagingObject() throws IOException {
    storage.deleteUpload(STAGING_KEY);

    verify(client).deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(STAGING_KEY).build());
    assertThatThrownBy(() -> storage.deleteUpload("profile-images/" + UUID + ".png"))
        .isInstanceOf(IOException.class);
  }

  private ResponseInputStream<GetObjectResponse> stagedResponse(Long length, byte[] bytes, AtomicBoolean aborted) {
    var response = GetObjectResponse.builder().contentType("image/png").contentLength(length).build();
    var contents = AbortableInputStream.create(new ByteArrayInputStream(bytes), () -> aborted.set(true));
    return new ResponseInputStream<>(response, contents);
  }
}
