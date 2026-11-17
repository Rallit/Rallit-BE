package com.rallit.domain.court.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rallit.global.storage.S3ImageObjects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class CourtThumbnailStorageConfigurationTest {
	@Test
	void s3ModeReadsTheCourtPrefixAndKeepsMissingObjectsEmpty() {
		var client = mock(S3Client.class);
		String key = "b24c343a-e0b3-4749-8e79-a548a53cc61c.jpg";
		var request = GetObjectRequest.builder().bucket("image-test").key("court-thumbnails/" + key).build();
		when(client.getObject(request)).thenThrow(NoSuchKeyException.builder().statusCode(404).build());

		new ApplicationContextRunner()
				.withUserConfiguration(CourtThumbnailStorageConfiguration.class)
				.withPropertyValues("rallit.profile-image.storage=s3")
				.withBean(S3ImageObjects.class, () -> new S3ImageObjects(client, "image-test"))
				.run(context -> {
					assertThat(context).hasSingleBean(CourtThumbnailStorage.class);
					assertThat(context.getBean(CourtThumbnailStorage.class).read(key)).isEmpty();
					verify(client).getObject(request);
				});
	}
}
