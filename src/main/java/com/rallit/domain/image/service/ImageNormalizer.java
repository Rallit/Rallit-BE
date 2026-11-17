package com.rallit.domain.image.service;

import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.user.exception.UserErrorStatus;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageNormalizer {
  public static final int MAX_BYTES = 5 * 1024 * 1024;
  private static final int MAX_DIMENSION = 4_096;
  private static final long MAX_PIXELS = 12_500_000L;
  private static final float JPEG_QUALITY = 0.82f;
  private static final int MAX_CONCURRENT_NORMALIZATIONS = 1;
  private static final int PNG_EXIF_CHUNK_TYPE = 0x65584966; // eXIf
  private static final int PNG_END_CHUNK_TYPE = 0x49454e44; // IEND
  private final Semaphore normalizationSlots = new Semaphore(MAX_CONCURRENT_NORMALIZATIONS);

  public NormalizedImage normalizeUpload(byte[] contents, String filename, ImageUploadPurpose purpose) {
    if (contents.length > MAX_BYTES) {
      throw new GeneralException(ErrorStatus._UPLOAD_TOO_LARGE);
    }
    if (contents.length == 0 || filename == null) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE);
    }
    if (!normalizationSlots.tryAcquire()) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE);
    }
    try {
      return normalize(contents, filename.toLowerCase(Locale.ROOT), purpose);
    } catch (IOException | ImageProcessingException | IllegalArgumentException failure) {
      throw new GeneralException(UserErrorStatus.INVALID_PROFILE_IMAGE, failure);
    } finally {
      normalizationSlots.release();
    }
  }

  private NormalizedImage normalize(byte[] original, String filename, ImageUploadPurpose purpose)
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
        var source = validatedSize(reader);
        int orientation = readOrientation(original, format);
        var target = new ImageSize(purpose.getOutputWidth(), purpose.getOutputHeight());
        var crop = source.oriented(orientation).cropTo(target);
        var outputSize = new ImageSize(Math.min(crop.width(), target.width()), Math.min(crop.height(), target.height()));
        var pixels = readPixels(reader, crop, outputSize);
        var image = render(pixels, orientation, outputSize, format.equals("png") && pixels.getColorModel().hasAlpha());
        return encode(image, format, purpose);
      } finally {
        reader.dispose();
      }
    }
  }

  private ImageSize validatedSize(ImageReader reader) throws IOException {
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
    return new ImageSize(width, height);
  }

  private BufferedImage readPixels(ImageReader reader, ImageSize crop, ImageSize output) throws IOException {
    var parameters = reader.getDefaultReadParam();
    // Decode enough pixels in the crop to reach the target without scaling up.
    int step = Math.min(crop.width() / output.width(), crop.height() / output.height());
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

  private BufferedImage render(BufferedImage source, int orientation, ImageSize outputSize, boolean hasAlpha) {
    int width = source.getWidth();
    int height = source.getHeight();
    var oriented = new ImageSize(width, height).oriented(orientation);
    double scale = Math.max((double) outputSize.width() / oriented.width(),
        (double) outputSize.height() / oriented.height());
    var transform = AffineTransform.getTranslateInstance((outputSize.width() - oriented.width() * scale) / 2,
        (outputSize.height() - oriented.height() * scale) / 2);
    transform.scale(scale, scale);
    transform.concatenate(switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
      default -> new AffineTransform();
    });

    int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    var output = new BufferedImage(outputSize.width(), outputSize.height(), type);
    var graphics = output.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return output;
  }

  private NormalizedImage encode(BufferedImage image, String inputFormat, ImageUploadPurpose purpose) throws IOException {
    var output = new ByteArrayOutputStream();
    if (inputFormat.equals("png") && (purpose == ImageUploadPurpose.PROFILE || hasTransparentPixels(image))) {
      if (!ImageIO.write(image, "png", output)) {
        throw new IOException("No PNG image writer");
      }
      return new NormalizedImage(".png", output.toByteArray());
    }

    var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (var stream = new MemoryCacheImageOutputStream(output)) {
      writer.setOutput(stream);
      var parameters = writer.getDefaultWriteParam();
      parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parameters.setCompressionQuality(JPEG_QUALITY);
      var rgb = image.getColorModel().hasAlpha() ? toRgb(image) : image;
      writer.write(null, new IIOImage(rgb, null, null), parameters);
    } finally {
      writer.dispose();
    }
    return new NormalizedImage(".jpg", output.toByteArray());
  }

  private boolean hasTransparentPixels(BufferedImage image) {
    if (!image.getColorModel().hasAlpha()) return false;
    for (int pixel : ((DataBufferInt) image.getRaster().getDataBuffer()).getData()) {
      if (pixel >>> 24 != 255) return true;
    }
    return false;
  }

  private BufferedImage toRgb(BufferedImage image) {
    var rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = rgb.createGraphics();
    try {
      graphics.drawImage(image, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return rgb;
  }

  private record ImageSize(int width, int height) {
    ImageSize oriented(int orientation) {
      return orientation >= 5 ? new ImageSize(height, width) : this;
    }

    ImageSize cropTo(ImageSize target) {
      int cropWidth = Math.min(width, Math.max(1, Math.round((float) height * target.width / target.height)));
      int cropHeight = Math.min(height, Math.max(1, Math.round((float) width * target.height / target.width)));
      return new ImageSize(cropWidth, cropHeight);
    }
  }

  public record NormalizedImage(String extension, byte[] contents) {}
}
