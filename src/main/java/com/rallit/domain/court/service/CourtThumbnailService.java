package com.rallit.domain.court.service;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourtThumbnailService {
	private final CourtRepository courts;

	public String imageKey(Long courtId) {
		return courts.findById(courtId).map(Court::getThumbnailImageKey)
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_THUMBNAIL_NOT_FOUND));
	}
}
