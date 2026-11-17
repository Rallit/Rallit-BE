package com.rallit.domain.image;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select upload from ImageUpload upload where upload.id = :id")
  Optional<ImageUpload> findByIdForUpdate(UUID id);
}
