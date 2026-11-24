package com.rallit.domain.image.service;

import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.image.dto.ImageUploadRequest;
import com.rallit.domain.image.dto.ImageUploadResponse;
import com.rallit.domain.image.entity.ImageUpload;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.exception.ImageUploadErrorStatus;
import com.rallit.domain.image.repository.ImageUploadRepository;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.ImageKey;
import com.rallit.global.storage.PresignedImageStorage;
import com.rallit.global.storage.S3ImageObjects;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageUploadService {
  private final ImageUploadRepository uploads;
  private final UserRepository users;
  private final CourtRepository courts;
  private final PresignedImageStorage presigned;
  private final S3ImageObjects objects;
  private final ImageNormalizer normalizer;
  private final PlatformTransactionManager transactions;
  private final Clock clock;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ImageUploadResponse initiate(Long ownerId, Long targetId, ImageUploadPurpose purpose,
      ImageUploadRequest request) {
    if (request.size() > ImageNormalizer.MAX_BYTES) {
      throw new GeneralException(ErrorStatus._UPLOAD_TOO_LARGE);
    }
    validateFileName(request);
    if (purpose == ImageUploadPurpose.COURT && !courts.existsById(targetId)) {
      throw new GeneralException(CourtErrorStatus.COURT_NOT_FOUND);
    }

    var id = UUID.randomUUID();
    try {
      var form = presigned.signUpload(stagingKey(id), MediaType.parseMediaType(request.contentType()),
          ImageNormalizer.MAX_BYTES);
      uploads.save(new ImageUpload(id, ownerId, targetId, purpose, request.contentType(),
          request.size(), form.expiresAt()));
      return new ImageUploadResponse(ImageUploadResponse.Mode.S3, id, form.url(), form.fields(),
          form.expiresAt());
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void complete(Long ownerId, Long targetId, ImageUploadPurpose purpose, UUID uploadId) {
    if (purpose == ImageUploadPurpose.RESERVATION_EVIDENCE) {
      throw new IllegalArgumentException("Reservation evidence is attached when its match is opened");
    }
    var upload = checkTicket(uploads.findById(uploadId), ownerId, targetId, purpose);
    if (upload.isCompleted()) return;

    var finalKey = writeStagedImage(upload);
    if (finalKey.isEmpty()) {
      if (checkTicket(uploads.findById(uploadId), ownerId, targetId, purpose).isCompleted()) return;
      throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_NOT_READY);
    }
    finish(ownerId, targetId, purpose, uploadId, finalKey.get());
    deleteStagingObject(stagingKey(uploadId));
  }

  // The stored image is deleted unless the transaction that consumes the ticket commits.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public <T> T attach(Long ownerId, Long targetId, ImageUploadPurpose purpose, UUID uploadId,
      Function<String, T> attachment) {
    var upload = checkTicket(uploads.findById(uploadId), ownerId, targetId, purpose);
    if (upload.isCompleted()) throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_ALREADY_USED);
    var written = writeStagedImage(upload);
    if (written.isEmpty()) {
      var status = checkTicket(uploads.findById(uploadId), ownerId, targetId, purpose).isCompleted()
          ? ImageUploadErrorStatus.IMAGE_UPLOAD_ALREADY_USED : ImageUploadErrorStatus.IMAGE_UPLOAD_NOT_READY;
      throw new GeneralException(status);
    }
    String finalKey = written.get();

    var update = new ImageKeyUpdate();
    T result;
    try {
      result = new TransactionTemplate(transactions).execute(status -> {
        update.register();
        var ticket = checkTicket(uploads.findByIdForUpdate(uploadId), ownerId, targetId, purpose);
        if (ticket.isCompleted()) throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_ALREADY_USED);
        T attached = attachment.apply(finalKey);
        update.replaced(null);
        ticket.complete(clock.instant());
        return attached;
      });
    } finally {
      deleteFinalObject(purpose, update.unreferencedKey(finalKey));
    }
    deleteStagingObject(stagingKey(uploadId));
    return result;
  }

  private Optional<String> writeStagedImage(ImageUpload upload) {
    try {
      var staged = presigned.readUpload(stagingKey(upload.getId()), ImageNormalizer.MAX_BYTES);
      if (staged.isEmpty()) return Optional.empty();
      var normalized = normalize(upload, staged.get());
      String finalKey = UUID.randomUUID() + normalized.extension();
      writeFinalObject(upload.getPurpose(), finalKey, normalized.contents());
      return Optional.of(finalKey);
    } catch (PresignedImageStorage.UploadTooLargeException oversized) {
      throw new GeneralException(ErrorStatus._UPLOAD_TOO_LARGE, oversized);
    } catch (IOException failure) {
      throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
    }
  }

  private void writeFinalObject(ImageUploadPurpose purpose, String finalKey, byte[] contents) throws IOException {
    try {
      objects.write(purpose.getPrefix(), finalKey, new ByteArrayInputStream(contents), contents.length,
          ImageKey.mediaType(finalKey));
    } catch (IOException failure) {
      // S3 may have written the object before the response was lost.
      deleteFinalObject(purpose, finalKey);
      throw failure;
    }
  }

  private void finish(Long ownerId, Long targetId, ImageUploadPurpose purpose, UUID uploadId, String finalKey) {
    var update = new ImageKeyUpdate();
    try {
      new TransactionTemplate(transactions).executeWithoutResult(status -> {
        update.register();
        var ticket = checkTicket(uploads.findByIdForUpdate(uploadId), ownerId, targetId, purpose);
        if (ticket.isCompleted()) return;

        String previous = switch (purpose) {
          case PROFILE -> users.findByIdForUpdate(targetId).filter(user -> !user.isWithdrawn())
              .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED)).replaceProfileImage(finalKey);
          case COURT -> courts.findByIdForUpdate(targetId)
              .orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_NOT_FOUND)).replaceThumbnail(finalKey);
          case RESERVATION_EVIDENCE ->
              throw new IllegalArgumentException("Reservation evidence is attached when its match is opened");
        };
        update.replaced(previous);
        ticket.complete(clock.instant());
      });
    } finally {
      deleteFinalObject(purpose, update.unreferencedKey(finalKey));
    }
  }

  private ImageUpload checkTicket(Optional<ImageUpload> result, Long ownerId, Long targetId,
      ImageUploadPurpose purpose) {
    var ticket = result.filter(upload -> upload.belongsTo(ownerId, targetId, purpose))
        .orElseThrow(() -> new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_NOT_FOUND));
    if (!ticket.isCompleted() && !clock.instant().isBefore(ticket.getExpiresAt())) {
      throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_EXPIRED);
    }
    return ticket;
  }

  private ImageNormalizer.NormalizedImage normalize(ImageUpload ticket,
      PresignedImageStorage.StagedImage staged) {
    if (staged.contents().length != ticket.getContentSize()
        || !ticket.getContentType().equals(staged.contentType())) {
      throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_INVALID_FILE);
    }
    String filename = MediaType.IMAGE_PNG_VALUE.equals(ticket.getContentType()) ? "upload.png" : "upload.jpg";
    return normalizer.normalizeUpload(staged.contents(), filename, ticket.getPurpose());
  }

  private static void validateFileName(ImageUploadRequest request) {
    String name = request.fileName().toLowerCase(Locale.ROOT);
    boolean matching = MediaType.IMAGE_PNG_VALUE.equals(request.contentType())
        ? name.endsWith(".png") : name.endsWith(".jpg") || name.endsWith(".jpeg");
    if (!matching) throw new GeneralException(ImageUploadErrorStatus.IMAGE_UPLOAD_INVALID_FILE);
  }

  private static String stagingKey(UUID id) {
    return "image-uploads/" + id;
  }

  private void deleteFinalObject(ImageUploadPurpose purpose, String key) {
    if (key == null) return;
    try {
      objects.delete(purpose.getPrefix(), key);
    } catch (IOException failure) {
      log.warn("Failed to remove an unreferenced image", failure);
    }
  }

  private void deleteStagingObject(String key) {
    try {
      presigned.deleteUpload(key);
    } catch (IOException failure) {
      log.warn("Failed to remove a staged image", failure);
    }
  }
}
