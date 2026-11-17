package com.rallit.domain.court.service;

import java.io.IOException;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.court.storage.CourtThumbnailStorage;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.storage.ImageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourtThumbnailService {
	private final CourtRepository courts;
	private final CourtThumbnailStorage storage;

	public Thumbnail get(Long courtId) {
		String key = imageKey(courtId);
		try {
			var contents = storage.read(key)
					.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_THUMBNAIL_NOT_FOUND));
			return new Thumbnail(contents, ImageKey.mediaType(key));
		} catch (IOException failure) {
			throw new GeneralException(ErrorStatus._SERVICE_UNAVAILABLE, failure);
		}
	}

	public String imageKey(Long courtId) {
		return courts.findById(courtId).map(Court::getThumbnailImageKey)
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_THUMBNAIL_NOT_FOUND));
	}

	public record Thumbnail(Resource contents, MediaType mediaType) {
	}
}
