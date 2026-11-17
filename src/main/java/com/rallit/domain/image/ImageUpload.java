package com.rallit.domain.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "image_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ImageUpload {
  @Id private UUID id;
  @Column(nullable = false) private Long ownerId;
  @Column(nullable = false) private Long targetId;
  @Column(nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private ImageUploadPurpose purpose;
  @Column(nullable = false, length = 32) private String contentType;
  @Column(nullable = false) private long contentSize;
  @Column(nullable = false) private Instant expiresAt;
  private Instant completedAt;

  ImageUpload(UUID id, Long ownerId, Long targetId, ImageUploadPurpose purpose, String contentType,
      long contentSize, Instant expiresAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.targetId = targetId;
    this.purpose = purpose;
    this.contentType = contentType;
    this.contentSize = contentSize;
    this.expiresAt = expiresAt;
  }

  boolean belongsTo(Long owner, Long target, ImageUploadPurpose purpose) {
    return ownerId.equals(owner) && targetId.equals(target) && this.purpose == purpose;
  }

  boolean isCompleted() {
    return completedAt != null;
  }

  void complete(Instant now) {
    completedAt = now;
  }
}
