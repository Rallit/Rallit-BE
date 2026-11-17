package com.rallit.domain.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ImageUploadRequest(
    @Schema(example = "photo.png", minLength = 1)
    @NotBlank @Size(max = 255) String fileName,
    @Schema(example = "image/png")
    @NotBlank @Pattern(regexp = "image/(jpeg|png)") String contentType,
    @Schema(description = "원본 이미지 바이트 수", example = "125000", maximum = "5242880")
    @NotNull @Positive Long size) {
}
