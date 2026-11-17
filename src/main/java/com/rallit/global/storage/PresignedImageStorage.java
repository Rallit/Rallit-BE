package com.rallit.global.storage;

import com.rallit.global.config.S3PresignedProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointProvider;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PresignedImageStorage {
  private static final Pattern STAGING_KEY = Pattern.compile(
      "image-uploads/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  private static final DateTimeFormatter SIGNING_DATE =
      DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SIGNING_TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final S3Client client;
  private final S3Presigner presigner;
  private final AwsCredentialsProvider credentialsProvider;
  private final ObjectMapper json;
  private final Clock clock;
  private final S3PresignedProperties properties;
  private final String bucket;
  private final String region;
  private final String uploadUrl;

  public PresignedImageStorage(S3Client client, S3Presigner presigner, AwsCredentialsProvider credentialsProvider,
      ObjectMapper json, Clock clock, S3PresignedProperties properties,
      @Value("${rallit.s3.bucket}") String bucket,
      @Value("${rallit.s3.region}") String region,
      @Value("${rallit.s3.endpoint:}") String endpoint) {
    Assert.hasText(bucket, "rallit.s3.bucket is required for S3 image storage");
    this.client = client;
    this.presigner = presigner;
    this.credentialsProvider = credentialsProvider;
    this.json = json;
    this.clock = clock;
    this.properties = properties;
    this.bucket = bucket;
    this.region = region;
    this.uploadUrl = S3EndpointProvider.defaultProvider().resolveEndpoint(params -> {
      params.bucket(bucket).region(Region.of(region));
      if (StringUtils.hasText(endpoint)) {
        params.endpoint(endpoint).forcePathStyle(true);
      }
    }).join().endpointUrl().toUri().toASCIIString();
  }

  public SignedUpload signUpload(String stagingKey, MediaType mediaType, long maximumBytes)
      throws IOException {
    requireStagingKey(stagingKey);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.uploadLifetime());
    String date = SIGNING_DATE.format(now);
    try {
      var credentials = credentialsProvider.resolveCredentials();
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("key", stagingKey);
      fields.put("Content-Type", mediaType.toString());
      fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
      fields.put("x-amz-credential", credentials.accessKeyId() + "/" + date + "/" + region + "/s3/aws4_request");
      fields.put("x-amz-date", SIGNING_TIMESTAMP.format(now));
      if (credentials instanceof AwsSessionCredentialsIdentity session) {
        fields.put("x-amz-security-token", session.sessionToken());
      }

      List<Object> conditions = new ArrayList<>();
      conditions.add(Map.of("bucket", bucket));
      fields.forEach((name, value) -> conditions.add(Map.of(name, value)));
      conditions.add(List.of("content-length-range", 1, maximumBytes));

      var document = new PostPolicy(expiresAt.toString(), conditions);
      String policy = Base64.getEncoder().encodeToString(json.writeValueAsBytes(document));
      fields.put("policy", policy);
      fields.put("x-amz-signature", signature(credentials.secretAccessKey(), date, policy));
      return new SignedUpload(uploadUrl, Map.copyOf(fields), expiresAt);
    } catch (SdkException | GeneralSecurityException | JacksonException failure) {
      throw new IOException("Could not sign staged S3 image upload", failure);
    }
  }

  public SignedRead signRead(String prefix, String key) throws IOException {
    var request = GetObjectRequest.builder().bucket(bucket).key(prefix + ImageKey.requireValid(key)).build();
    try {
      var signed = presigner.presignGetObject(
          builder -> builder.signatureDuration(properties.downloadLifetime()).getObjectRequest(request));
      if (!signed.isBrowserExecutable()) {
        throw new IOException("Signed S3 image read requires additional request headers");
      }
      return new SignedRead(signed.url().toExternalForm(), signed.expiration());
    } catch (SdkException failure) {
      throw new IOException("Could not sign S3 image read", failure);
    }
  }

  public Optional<StagedImage> readUpload(String stagingKey, int maximumBytes) throws IOException {
    requireStagingKey(stagingKey);
    var request = GetObjectRequest.builder().bucket(bucket).key(stagingKey).build();
    try (var stream = client.getObject(request)) {
      Long length = stream.response().contentLength();
      if (length != null && length > maximumBytes) {
        stream.abort();
        throw new UploadTooLargeException();
      }
      byte[] contents = stream.readNBytes(maximumBytes);
      if (contents.length == maximumBytes && stream.read() != -1) {
        stream.abort();
        throw new UploadTooLargeException();
      }
      return Optional.of(new StagedImage(contents, stream.response().contentType()));
    } catch (NoSuchKeyException missing) {
      return Optional.empty();
    } catch (SdkException failure) {
      throw new IOException("Could not read staged image from S3", failure);
    }
  }

  public void deleteUpload(String stagingKey) throws IOException {
    requireStagingKey(stagingKey);
    var request = DeleteObjectRequest.builder().bucket(bucket).key(stagingKey).build();
    try {
      client.deleteObject(request);
    } catch (SdkException failure) {
      throw new IOException("Could not delete staged image from S3", failure);
    }
  }

  private String signature(String secretKey, String date, String policy) throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
    byte[] dateKey = hmac(mac, ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
    byte[] regionKey = hmac(mac, dateKey, region);
    byte[] serviceKey = hmac(mac, regionKey, "s3");
    byte[] signingKey = hmac(mac, serviceKey, "aws4_request");
    return HexFormat.of().formatHex(hmac(mac, signingKey, policy));
  }

  private static byte[] hmac(Mac mac, byte[] key, String value) throws GeneralSecurityException {
    mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void requireStagingKey(String key) throws IOException {
    if (key == null || !STAGING_KEY.matcher(key).matches()) {
      throw new IOException("Invalid staged image key");
    }
  }

  public record SignedUpload(String url, Map<String, String> fields, Instant expiresAt) {}

  public record SignedRead(String url, Instant expiresAt) {}

  public record StagedImage(byte[] contents, String contentType) {}

  public static class UploadTooLargeException extends IOException {
    public UploadTooLargeException() {
      super("Staged image exceeds the maximum upload size");
    }
  }

  private record PostPolicy(String expiration, List<Object> conditions) {}
}
