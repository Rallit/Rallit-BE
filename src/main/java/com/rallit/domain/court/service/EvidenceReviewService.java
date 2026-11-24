package com.rallit.domain.court.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.rallit.domain.court.dto.EvidenceReviewItemResponse;
import com.rallit.domain.court.dto.EvidenceReviewResponse;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.enums.EvidenceReviewStatus;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtSlotRepository;
import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageLinkService;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

	private final CourtSlotRepository slots;
	private final MatchRepository matches;
	private final ImageLinkService links;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<EvidenceReviewItemResponse> list(EvidenceReviewStatus status, int page, int size) {
		return PageResponse.from(slots.findAllByEvidenceReviewStatus(status, PageRequest.of(page, size))
				.map(slot -> EvidenceReviewItemResponse.from(slot)));
	}

	public ImageLinkResponse evidenceImage(Long courtSlotId) {
		String key = slots.findById(courtSlotId)
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_SLOT_NOT_FOUND))
				.getEvidenceFileKey();
		if (key == null) {
			throw new GeneralException(CourtErrorStatus.EVIDENCE_NOT_SUBMITTED);
		}
		return links.link(ImageUploadPurpose.RESERVATION_EVIDENCE, key);
	}

	@Transactional
	public EvidenceReviewResponse approve(Long courtSlotId) {
		var slot = lock(courtSlotId);
		slot.approveEvidence(now());
		return EvidenceReviewResponse.from(slot);
	}

	@Transactional
	public EvidenceReviewResponse reject(Long courtSlotId, String reason) {
		var slot = lock(courtSlotId);
		var now = now();
		slot.rejectEvidence(reason.strip(), now);
		matches.findByCourtSlotIdForUpdate(courtSlotId)
				.ifPresent(match -> match.cancel(slot.getRejectionReason(), now));
		return EvidenceReviewResponse.from(slot);
	}

	private CourtSlot lock(Long courtSlotId) {
		return slots.findByIdForUpdate(courtSlotId)
				.orElseThrow(() -> new GeneralException(CourtErrorStatus.COURT_SLOT_NOT_FOUND));
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
