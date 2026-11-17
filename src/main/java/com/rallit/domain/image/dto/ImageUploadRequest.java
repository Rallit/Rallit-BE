package com.rallit.domain.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ImageUploadRequest(
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank @Pattern(regexp = "image/(jpeg|png)") String contentType,
    @NotNull @Positive Long size) {
}
