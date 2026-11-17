package com.rallit.global.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ImageObjects {
  private final S3Client client;
  private final String bucket;

  public S3ImageObjects(S3Client client, @Value("${rallit.s3.bucket}") String bucket) {
    Assert.hasText(bucket, "rallit.s3.bucket is required for S3 image storage");
    this.client = client;
    this.bucket = bucket;
  }

  public void write(String prefix, String key, InputStream contents, long contentLength, MediaType mediaType)
      throws IOException {
    var request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(prefix + ImageKey.requireValid(key))
        .contentType(mediaType.toString())
        .ifNoneMatch("*")
        .build();
    try {
      client.putObject(request, RequestBody.fromInputStream(contents, contentLength));
    } catch (SdkException failure) {
      throw new IOException("Could not write image to S3", failure);
    }
  }

  public void delete(String prefix, String key) throws IOException {
    var request = DeleteObjectRequest.builder().bucket(bucket).key(prefix + ImageKey.requireValid(key)).build();
    try {
      client.deleteObject(request);
    } catch (SdkException failure) {
      throw new IOException("Could not delete image from S3", failure);
    }
  }
}
