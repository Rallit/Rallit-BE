package com.rallit.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "s3")
class S3ImageStorageConfiguration {
  @Bean
  S3Client imageS3Client(@Value("${rallit.image-storage.s3.region}") String region) {
    Assert.hasText(region, "rallit.image-storage.s3.region is required for S3 image storage");
    return S3Client.builder().region(Region.of(region)).build();
  }
}
