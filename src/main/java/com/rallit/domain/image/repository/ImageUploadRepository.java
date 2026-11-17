package com.rallit.domain.image.repository;

import com.rallit.domain.image.entity.ImageUpload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select upload from ImageUpload upload where upload.id = :id")
  Optional<ImageUpload> findByIdForUpdate(UUID id);
}
