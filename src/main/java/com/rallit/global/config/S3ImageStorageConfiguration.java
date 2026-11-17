package com.rallit.global.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "s3")
class S3ImageStorageConfiguration {
  @Bean
  DefaultCredentialsProvider imageS3CredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean
  S3Client imageS3Client(AwsCredentialsProvider credentialsProvider,
      @Value("${rallit.image-storage.s3.region}") String region,
      @Value("${rallit.image-storage.s3.endpoint:}") String endpoint) {
    var builder = S3Client.builder().region(imageRegion(region)).credentialsProvider(credentialsProvider);
    if (StringUtils.hasText(endpoint)) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }
    return builder.build();
  }

  @Bean
  S3Presigner imageS3Presigner(AwsCredentialsProvider credentialsProvider,
      @Value("${rallit.image-storage.s3.region}") String region,
      @Value("${rallit.image-storage.s3.endpoint:}") String endpoint) {
    var builder = S3Presigner.builder().region(imageRegion(region)).credentialsProvider(credentialsProvider);
    if (StringUtils.hasText(endpoint)) {
      builder.endpointOverride(URI.create(endpoint)).serviceConfiguration(S3Configuration.builder()
          .pathStyleAccessEnabled(true)
          .build());
    }
    return builder.build();
  }

  private static Region imageRegion(String region) {
    Assert.hasText(region, "rallit.image-storage.s3.region is required for S3 image storage");
    return Region.of(region);
  }
}
