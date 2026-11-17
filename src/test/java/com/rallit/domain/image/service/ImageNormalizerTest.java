package com.rallit.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ImageNormalizerTest {
  private final ImageNormalizer normalizer = new ImageNormalizer();

  @ParameterizedTest
  @CsvSource({"4097, 2, PROFILE", "4096, 3200, PROFILE", "4097, 2, COURT", "4096, 3200, COURT"})
  void rejectsImagesAboveTheSourceDimensionOrPixelLimit(int width, int height, ImageUploadPurpose purpose)
      throws IOException {
    var file = png(width, height);

    assertThatThrownBy(() -> normalizer.normalizeUpload(file, "photo.png", purpose))
        .isInstanceOfSatisfying(GeneralException.class,
            failure -> assertThat(failure.getErrorReasonHttpStatus().getCode()).isEqualTo("COMMON400"))
        .hasMessage("사진은 가로·세로 각각 4,096픽셀, 총 1,250만 픽셀까지 올릴 수 있습니다.");
  }

  @ParameterizedTest
  @CsvSource({"321, 321, 320", "321, 639, 320", "640, 321, 320", "63, 48, 48"})
  void cropsAndResizesProfilesWithoutUpscaling(int width, int height, int expected) throws IOException {
    var normalized = normalizer.normalizeUpload(png(width, height), "avatar.png", ImageUploadPurpose.PROFILE);

    var image = ImageIO.read(new ByteArrayInputStream(normalized.contents()));
    assertThat(image.getWidth()).isEqualTo(expected);
    assertThat(image.getHeight()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void choosesCourtFormatByPixelTransparencyWithoutUpscaling(boolean transparent) throws IOException {
    var source = new BufferedImage(32, 18, BufferedImage.TYPE_INT_ARGB);
    var graphics = source.createGraphics();
    try {
      graphics.fillRect(0, 0, 32, 18);
    } finally {
      graphics.dispose();
    }
    if (transparent) source.setRGB(16, 9, 0);
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(source, "png", bytes);

    var normalized = normalizer.normalizeUpload(bytes.toByteArray(), "court.png", ImageUploadPurpose.COURT);

    assertThat(normalized.extension()).isEqualTo(transparent ? ".png" : ".jpg");
    var image = ImageIO.read(new ByteArrayInputStream(normalized.contents()));
    assertThat(image.getWidth()).isEqualTo(32);
    assertThat(image.getHeight()).isEqualTo(18);
    assertThat(image.getRGB(16, 9) >>> 24).isEqualTo(transparent ? 0 : 255);
  }

  @Test
  void rejectsASecondConcurrentImageAndReleasesTheSlot() throws Exception {
    var slot = (Semaphore) ReflectionTestUtils.getField(normalizer, "normalizationSlots");
    assertThat(slot).isNotNull();
    var photo = png(2, 2);
    slot.acquire();
    try {
      assertThatThrownBy(() -> normalizer.normalizeUpload(photo, "avatar.png", ImageUploadPurpose.PROFILE))
          .isInstanceOfSatisfying(GeneralException.class,
              failure -> assertThat(failure.getCode()).isEqualTo(ErrorStatus._SERVICE_UNAVAILABLE));
    } finally {
      slot.release();
    }
    assertThat(normalizer.normalizeUpload(photo, "avatar.png", ImageUploadPurpose.PROFILE).extension()).isEqualTo(".png");
    assertThat(slot.availablePermits()).isEqualTo(1);
  }

  private byte[] png(int width, int height) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", bytes);
    return bytes.toByteArray();
  }
}
