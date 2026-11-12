package com.rallit.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.domain.user.storage.FileSystemProfileImageStorage;
import com.rallit.support.IntegrationTestSupport;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
    "RALLIT_ENV_FILE=.env.test-disabled",
    "rallit.profile-image.storage-path=./build/profile-image-integration"
})
@AutoConfigureMockMvc
class ProfileImageIntegrationTest extends IntegrationTestSupport {
  private static final String PATH = "/api/v1/users/me/profile-image";

  @Autowired MockMvc mvc;
  @Autowired UserRegistrationService users;
  @Autowired TokenSessionService sessions;
  @MockitoSpyBean UserRepository userRepository;
  @MockitoSpyBean FileSystemProfileImageStorage storage;

  @Test
  void uploadsServesAndDeletesAnImageDuringOnboarding() throws Exception {
    var owner = login();
    var viewer = login();
    var source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    source.setRGB(0, 0, 0x80402010);
    var bytes = encode(source, "png");
    var url = "/api/v1/users/" + owner.id() + "/profile-image";

    upload(owner, "avatar.png", "image/png", bytes)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    mvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer()))
        .andExpect(jsonPath("$.result.profileImageUrl").value(url));
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    var served = view(viewer, url, "image/png");
    assertThat(ImageIO.read(new ByteArrayInputStream(served)).getRGB(0, 0))
        .isEqualTo(ImageIO.read(new ByteArrayInputStream(bytes)).getRGB(0, 0));

    String previous = userRepository.findById(owner.id()).orElseThrow().getProfileImageKey();
    upload(owner, "replacement.jpeg", "image/jpeg", image("jpeg")).andExpect(status().isOk());
    assertThat(storage.read(previous)).isEmpty();
    view(viewer, url, "image/jpeg");

    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer()))
        .andExpect(jsonPath("$.result.profileImageUrl").value(nullValue()));
    mvc.perform(get(url).header("Authorization", viewer.bearer())).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"jpeg", "png"})
  void appliesExifOrientationAndRemovesLocationMetadata(String format) throws Exception {
    var owner = login();
    var viewer = login();
    var image = new BufferedImage(48, 24, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(Color.RED);
      graphics.fillRect(0, 0, 24, 24);
      graphics.setColor(Color.BLUE);
      graphics.fillRect(24, 0, 24, 24);
    } finally {
      graphics.dispose();
    }
    byte[] tiff = rotatedExifWithGps();
    byte[] pixels = encode(image, format);
    boolean jpeg = format.equals("jpeg");
    byte[] original = jpeg ? withJpegExif(pixels, tiff) : withPngExif(pixels, tiff);
    String marker = jpeg ? "Exif\0\0" : "eXIf";
    String type = "image/" + format;
    String url = "/api/v1/users/" + owner.id() + "/profile-image";
    assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains(marker);
    var originalGps = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original))
        .getFirstDirectoryOfType(GpsDirectory.class);
    assertThat(originalGps).isNotNull();
    assertThat(originalGps.containsTag(GpsDirectory.TAG_LATITUDE)).isTrue();

    upload(owner, "avatar." + format, type, original).andExpect(status().isOk());
    byte[] served = view(viewer, url, type);
    assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain(marker);
    assertThat(ImageMetadataReader.readMetadata(new ByteArrayInputStream(served))
        .getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    var rotated = ImageIO.read(new ByteArrayInputStream(served));
    assertThat(rotated.getWidth()).isEqualTo(24);
    assertThat(rotated.getHeight()).isEqualTo(48);
    assertThat(new Color(rotated.getRGB(12, 12)).getRed()).isGreaterThan(220);
    assertThat(new Color(rotated.getRGB(12, 36)).getBlue()).isGreaterThan(220);
    mvc.perform(delete(PATH).header("Authorization", owner.bearer())).andExpect(status().isNoContent());
  }

  @Test
  void rejectsOversizedOrDisguisedUploadsAndKeepsTheExistingImage() throws Exception {
    var owner = login();
    var original = image("jpeg");
    var url = "/api/v1/users/" + owner.id() + "/profile-image";
    upload(owner, "avatar.jpeg", "image/jpeg", original).andExpect(status().isOk());
    var stored = view(owner, url, "image/jpeg");
    upload(owner, "avatar.png", "image/png", original).andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", Arrays.copyOf(image("png"), 33))
        .andExpect(status().isBadRequest());
    upload(owner, "avatar.png", "image/png", new byte[5 * 1024 * 1024 + 1])
        .andExpect(status().is(413));
    assertThat(view(owner, url, "image/jpeg")).isEqualTo(stored);
    mvc.perform(delete(PATH).header("Authorization", owner.bearer()))
        .andExpect(status().isNoContent());
  }

  @Test
  void writesBeforeTransactionAndCleansUpReplacementOnRollback() throws Exception {
    var owner = login();
    var url = "/api/v1/users/" + owner.id() + "/profile-image";
    byte[] original = image("png");
    upload(owner, "avatar.png", "image/png", original).andExpect(status().isOk());
    byte[] stored = view(owner, url, "image/png");

    var replacementKey = new AtomicReference<String>();
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      replacementKey.set(invocation.getArgument(0));
      return invocation.callRealMethod();
    }).when(storage).write(anyString(), any());
    doReturn(Optional.empty()).when(userRepository).findByIdForUpdate(owner.id());

    upload(owner, "replacement.jpeg", "image/jpeg", image("jpeg")).andExpect(status().isUnauthorized());

    assertThat(replacementKey.get()).isNotNull();
    verify(storage).delete(replacementKey.get());
    assertThat(storage.read(replacementKey.get())).isEmpty();
    assertThat(view(owner, url, "image/png")).isEqualTo(stored);
  }

  @Test
  void distinguishesMissingFileFromStorageFailure() throws Exception {
    var owner = login();
    var url = "/api/v1/users/" + owner.id() + "/profile-image";
    upload(owner, "avatar.png", "image/png", image("png")).andExpect(status().isOk());
    var key = userRepository.findById(owner.id()).orElseThrow().getProfileImageKey();
    storage.delete(key);
    assertThat(storage.read(key)).isEmpty();
    mvc.perform(get(url).header("Authorization", owner.bearer())).andExpect(status().isNotFound());

    doThrow(new IOException("test storage outage")).when(storage).read(key);
    mvc.perform(get(url).header("Authorization", owner.bearer())).andExpect(status().isServiceUnavailable());
  }

  private byte[] view(Member viewer, String url, String type) throws Exception {
    return mvc.perform(get(url).header("Authorization", viewer.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(type))
        .andReturn().getResponse().getContentAsByteArray();
  }

  private ResultActions upload(Member member, String filename, String type, byte[] bytes)
      throws Exception {
    return mvc.perform(multipart(HttpMethod.PUT, PATH)
        .file(new MockMultipartFile("file", filename, type, bytes))
        .header("Authorization", member.bearer()));
  }

  private Member login() {
    var user = users.getOrCreateUser(kakao(UUID.randomUUID().toString()));
    return new Member(user.getId(),
        "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken());
  }

  private byte[] image(String format) throws IOException {
    return encode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format);
  }

  private byte[] encode(BufferedImage image, String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, format, bytes);
    return bytes.toByteArray();
  }

  private byte[] rotatedExifWithGps() {
    // Little-endian TIFF with IFD0 orientation 6 and a GPS IFD containing latitude.
    var tiff = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN);
    tiff.putShort((short) 0x4949).putShort((short) 42).putInt(8);
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putInt(6);
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38).putInt(0);
    tiff.putShort((short) 2);
    tiff.putShort((short) 1).putShort((short) 2).putInt(2).putInt('N');
    tiff.putShort((short) 2).putShort((short) 5).putInt(3).putInt(68).putInt(0);
    tiff.putInt(37).putInt(1).putInt(30).putInt(1).putInt(0).putInt(1);
    return tiff.array();
  }

  private byte[] withJpegExif(byte[] jpeg, byte[] tiff) {
    byte[] preamble = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
    return ByteBuffer.allocate(jpeg.length + 4 + preamble.length + tiff.length)
        .put(jpeg, 0, 2)
        .putShort((short) 0xffe1).putShort((short) (2 + preamble.length + tiff.length))
        .put(preamble).put(tiff)
        .put(jpeg, 2, jpeg.length - 2).array();
  }

  private byte[] withPngExif(byte[] png, byte[] tiff) {
    byte[] type = "eXIf".getBytes(StandardCharsets.US_ASCII);
    var crc = new CRC32();
    crc.update(type);
    crc.update(tiff);
    int afterHeader = 33;
    return ByteBuffer.allocate(png.length + 12 + tiff.length)
        .put(png, 0, afterHeader)
        .putInt(tiff.length).put(type).put(tiff).putInt((int) crc.getValue())
        .put(png, afterHeader, png.length - afterHeader).array();
  }

  private record Member(long id, String bearer) {}
}
