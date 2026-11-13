package com.rallit.domain.user.service;

import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.rallit.domain.user.exception.UserErrorStatus;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.awt.AlphaComposite;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class ProfileImageNormalizer {
  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final int MAX_DIMENSION = 4_096;
  private static final long MAX_PIXELS = 12_500_000L;
  private static final int OUTPUT_MAX_DIMENSION = 1_024;
  private static final int MAX_CONCURRENT_NORMALIZATIONS = 2;
  private static final int PNG_EXIF_CHUNK_TYPE = 0x65584966; // eXIf
  private static final int PNG_END_CHUNK_TYPE = 0x49454e44; // IEND
  private final Semaphore normalizationSlots = new Semaphore(MAX_CONCURRENT_NORMALIZATIONS);

  NormalizedImage normalize(MultipartFile file) {
    if (file.getSize() > MAX_BYTES) {
      throw new GeneralException(ErrorStatus._UPLOAD_TOO_LARGE);
    }
    String filename = file.getOriginalFilename();
    if (file.isEmpty() || filename == null) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
    }
    if (!normalizationSlots.tryAcquire()) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE);
    }
    try {
      return normalize(file.getBytes(), filename.toLowerCase(Locale.ROOT));
    } catch (IOException | ImageProcessingException | IllegalArgumentException failure) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE, failure);
    } finally {
      normalizationSlots.release();
    }
  }

  private NormalizedImage normalize(byte[] original, String filename)
      throws IOException, ImageProcessingException {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
      }
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = validatedFormat(reader.getFormatName(), filename);
        var pixels = readPixels(reader);
        int orientation = readOrientation(original, format);
        var oriented = orient(pixels, orientation, format.equals("jpeg"));
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(oriented, format, output)) {
          throw new IOException("No profile image writer for " + format);
        }
        return new NormalizedImage(format.equals("jpeg") ? ".jpg" : ".png", output.toByteArray());
      } finally {
        reader.dispose();
      }
    }
  }

  private BufferedImage readPixels(ImageReader reader) throws IOException {
    reader.addIIOReadWarningListener((source, warning) -> {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
    });
    int width = reader.getWidth(0);
    int height = reader.getHeight(0);
    if (width <= 0 || height <= 0) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
    }
    // A reader may allocate memory for the original image even when subsampling.
    if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
      throw new GeneralException(UserErrorStatus.PROFILE_IMAGE_DIMENSIONS_TOO_LARGE);
    }
    var parameters = reader.getDefaultReadParam();
    int step = Math.ceilDiv(Math.max(width, height), OUTPUT_MAX_DIMENSION);
    parameters.setSourceSubsampling(step, step, 0, 0);
    return reader.read(0, parameters);
  }

  private String validatedFormat(String format, String filename) {
    if ("png".equalsIgnoreCase(format) && filename.endsWith(".png")) return "png";
    if ("jpeg".equalsIgnoreCase(format) && (filename.endsWith(".jpeg") || filename.endsWith(".jpg"))) {
      return "jpeg";
    }
    throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
  }

  private int readOrientation(byte[] original, String format) throws IOException, ImageProcessingException {
    Metadata metadata = format.equals("jpeg")
        ? JpegMetadataReader.readMetadata(new ByteArrayInputStream(original), List.of(new ExifReader()))
        : readPngExif(original);
    var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
    Integer orientation = exif == null ? null : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
    return orientation != null && orientation >= 2 && orientation <= 8 ? orientation : 1;
  }

  private Metadata readPngExif(byte[] original) {
    var metadata = new Metadata();
    var chunks = ByteBuffer.wrap(original).position(8); // Skip the PNG signature.
    while (chunks.remaining() >= 12) {
      int length = chunks.getInt();
      int type = chunks.getInt();
      if (length < 0 || length > chunks.remaining() - 4 || type == PNG_END_CHUNK_TYPE) break;
      if (type == PNG_EXIF_CHUNK_TYPE) {
        byte[] exif = new byte[length];
        chunks.get(exif);
        new ExifReader().extract(new ByteArrayReader(exif), metadata);
        break;
      }
      chunks.position(chunks.position() + length + 4); // Skip chunk contents and CRC.
    }
    return metadata;
  }

  private BufferedImage orient(BufferedImage source, int orientation, boolean jpeg) {
    int width = source.getWidth();
    int height = source.getHeight();
    boolean swapDimensions = orientation >= 5;
    int type = !jpeg && source.getColorModel().hasAlpha()
        ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    var output = new BufferedImage(swapDimensions ? height : width, swapDimensions ? width : height, type);
    var transform = switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
      default -> new AffineTransform();
    };
    var graphics = output.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return output;
  }

  record NormalizedImage(String extension, byte[] contents) {}
}
