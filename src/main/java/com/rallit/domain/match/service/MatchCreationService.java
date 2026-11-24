package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.court.exception.CourtErrorStatus;
import com.rallit.domain.court.repository.CourtConstraintViolations;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.court.repository.CourtSlotRepository;
import com.rallit.domain.image.enums.ImageUploadPurpose;
import com.rallit.domain.image.service.ImageUploadService;
import com.rallit.domain.match.dto.request.MatchCreateRequest;
import com.rallit.domain.match.dto.response.MatchCreateResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MatchCreationService {

	private final UserRepository users;
	private final CourtRepository courts;
	private final CourtSlotRepository courtSlots;
	private final MatchRepository matches;
	private final ImageUploadService images;
	private final PlatformTransactionManager transactions;
	private final Clock clock;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MatchCreateResponse create(Long userId, MatchCreateRequest request) {
		var host = users.findById(userId).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
		if (!host.isOnboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_CREATE_ONBOARDING_REQUIRED);
		}
		var reservation = request.reservation();
		var schedule = MatchCreationRules.schedule(reservation, now());
		MatchCreationRules.validateRecruitment(request.recruitment(), host.getGender());
		if (!courts.existsById(reservation.courtId())) {
			throw new GeneralException(CourtErrorStatus.COURT_NOT_FOUND);
		}
		try {
			if (reservation.evidenceUploadId() == null) {
				return new TransactionTemplate(transactions).execute(status -> open(userId, request, schedule, null));
			}
			return images.attach(userId, userId, ImageUploadPurpose.RESERVATION_EVIDENCE,
					reservation.evidenceUploadId(), evidenceKey -> open(userId, request, schedule, evidenceKey));
		} catch (DataIntegrityViolationException failure) {
			if (CourtConstraintViolations.isSlotOverlap(failure)) {
				throw new GeneralException(CourtErrorStatus.COURT_SLOT_OVERLAPPED, failure);
			}
			throw failure;
		}
	}

	private MatchCreateResponse open(Long userId, MatchCreateRequest request, MatchCreationRules.Schedule schedule,
			String evidenceKey) {
		var reservation = request.reservation();
		var recruitment = request.recruitment();
		var host = users.getReferenceById(userId);
		var slot = courtSlots.save(CourtSlot.builder()
				.court(courts.getReferenceById(reservation.courtId()))
				.registrant(host)
				.courtNumber(reservation.courtNumber().strip())
				.startsAt(schedule.startsAt())
				.endsAt(schedule.endsAt())
				.evidenceFileKey(evidenceKey)
				.build());
		var match = matches.saveAndFlush(Match.builder()
				.courtSlot(slot)
				.host(host)
				.matchTypes(recruitment.matchTypes())
				.sameGenderOnly(recruitment.sameGenderOnly())
				.maleRecruitCount(recruitment.maleRecruitCount())
				.femaleRecruitCount(recruitment.femaleRecruitCount())
				.minNtrp(recruitment.minNtrp())
				.maxNtrp(recruitment.maxNtrp())
				.preferredAgeGroups(recruitment.preferredAgeGroups())
				.participationFee(recruitment.participationFee())
				.recruitmentDeadline(schedule.recruitmentDeadline())
				.build());
		return MatchCreateResponse.from(match);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
