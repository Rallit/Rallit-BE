package com.rallit.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

class ProfileImageNormalizerTest {
  private final ProfileImageNormalizer normalizer = new ProfileImageNormalizer();

  @ParameterizedTest
  @CsvSource({"4097, 2", "4096, 3200"})
  void rejectsImagesAboveTheSourceDimensionOrPixelLimit(int width, int height) throws IOException {
    var file = png(width, height);

    assertThatThrownBy(() -> normalizer.normalize(file))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("4,096");
  }

  @Test
  void rejectsASecondConcurrentImageAndReleasesTheSlot() throws Exception {
    var file = spy(png(2, 2));
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    doAnswer(call -> {
      started.countDown();
      assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue();
      return call.callRealMethod();
    }).when(file).getBytes();

    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> normalizer.normalize(file));
      try {
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        var second = png(2, 2);
        assertThatThrownBy(() -> normalizer.normalize(second))
            .isInstanceOfSatisfying(GeneralException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(ErrorStatus._SERVICE_UNAVAILABLE));
      } finally {
        finish.countDown();
      }
      assertThat(first.get(10, TimeUnit.SECONDS).extension()).isEqualTo(".png");
    }
    assertThat(normalizer.normalize(png(2, 2)).extension()).isEqualTo(".png");
  }

  private MockMultipartFile png(int width, int height) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", bytes);
    return new MockMultipartFile("file", "avatar.png", "image/png", bytes.toByteArray());
  }
}
