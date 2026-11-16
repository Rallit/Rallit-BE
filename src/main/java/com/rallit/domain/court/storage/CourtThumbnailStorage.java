package com.rallit.domain.court.storage;

import java.io.IOException;
import java.util.Optional;
import org.springframework.core.io.Resource;

@FunctionalInterface
public interface CourtThumbnailStorage {
	Optional<Resource> read(String key) throws IOException;
}
