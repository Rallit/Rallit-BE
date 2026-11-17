package com.rallit.domain.image;

import java.time.Instant;

public record ImageLinkResponse(String url, Instant expiresAt) {
}
