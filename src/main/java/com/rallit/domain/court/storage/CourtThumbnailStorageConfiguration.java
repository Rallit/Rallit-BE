package com.rallit.domain.court.storage;

import com.rallit.global.storage.FileSystemImageStorage;
import com.rallit.global.storage.S3ImageObjects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CourtThumbnailStorageConfiguration {
	@Bean
	@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "filesystem", matchIfMissing = true)
	CourtThumbnailStorage fileSystemCourtThumbnailStorage(
			@Value("${rallit.court-thumbnail.storage-path}") String directory) {
		return new FileSystemImageStorage(directory)::read;
	}

	@Bean
	@ConditionalOnProperty(name = "rallit.image-storage.backend", havingValue = "s3")
	CourtThumbnailStorage s3CourtThumbnailStorage(S3ImageObjects objects) {
		return key -> objects.read("court-thumbnails/", key);
	}
}
