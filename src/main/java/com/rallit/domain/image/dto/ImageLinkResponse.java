package com.rallit.domain.image.dto;

import java.time.Instant;

public record ImageLinkResponse(String url, Instant expiresAt) {
}
