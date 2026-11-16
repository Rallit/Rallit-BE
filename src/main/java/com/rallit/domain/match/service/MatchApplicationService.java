package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.rallit.domain.match.dto.response.HostMatchApplicationListResponse;
import com.rallit.domain.match.dto.response.MatchApplicationAcceptResponse;
import com.rallit.domain.match.dto.response.MatchApplicationCancelResponse;
import com.rallit.domain.match.dto.response.MatchApplicationCreateResponse;
import com.rallit.domain.match.dto.response.MatchApplicationDetailResponse;
import com.rallit.domain.match.dto.response.MatchApplicationRejectResponse;
import com.rallit.domain.match.dto.response.MyMatchApplicationListItemResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationRejectReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.match.exception.MatchApplicationNotCancelableException;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationConstraintViolations;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchApplicationRepository.MatchApplicationCount;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchApplicationService {

	private static final Duration PAYMENT_DUE_DURATION = Duration.ofHours(24);

	private final MatchApplicationRepository matchApplicationRepository;
	private final MatchRepository matchRepository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final MatchApplicationEligibility eligibility;

	@Transactional
	public MatchApplicationCreateResponse apply(Long userId, Long matchId) {
		User applicant = userRepository.findById(userId)
				.orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
		if (!applicant.isOnboardingCompleted()) {
			throw new GeneralException(MatchApplicationErrorStatus.ONBOARDING_REQUIRED);
		}
		Match match = matchRepository.findById(matchId)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_NOT_FOUND));
		LocalDateTime now = now();
		eligibility.blockReason(match, applicant, now).ifPresent(reason -> {
			throw new GeneralException(reason);
		});
		try {
			MatchApplication application = matchApplicationRepository.saveAndFlush(
					MatchApplication.create(match, applicant, now));
			return MatchApplicationCreateResponse.from(application);
		} catch (DataIntegrityViolationException failure) {
			if (!MatchApplicationConstraintViolations.isActiveApplicationConflict(failure)) {
				throw failure;
			}
			throw new GeneralException(MatchApplicationErrorStatus.APPLICATION_DUPLICATED, failure);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<MyMatchApplicationListItemResponse> getMyScheduledApplications(Long userId, LocalDate date, int page,
			int size) {
		LocalDateTime from = (date == null ? LocalDate.now(clock) : date).atStartOfDay();
		LocalDateTime to = date == null ? null : from.plusDays(1);
		return listResponse(matchApplicationRepository.findScheduledByApplicantId(userId,
				MatchApplicationStatus.ACTIVE, from, to, PageRequest.of(page, size)));
	}

	@Transactional(readOnly = true)
	public PageResponse<MyMatchApplicationListItemResponse> getMyClosedApplications(Long userId, int page, int size) {
		return listResponse(matchApplicationRepository.findRecentByApplicantId(userId,
				MatchApplicationStatus.CLOSED, PageRequest.of(page, size)));
	}

	@Transactional(readOnly = true)
	public MatchApplicationDetailResponse getApplication(Long userId, Long applicationId) {
		MatchApplication application = matchApplicationRepository.findDetailByIdAndApplicantId(applicationId, userId)
				.orElseThrow(() -> new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_FOUND));
		List<MatchApplication> confirmedApplications = application.getStatus() == MatchApplicationStatus.CONFIRMED
				? matchApplicationRepository.findAllWithApplicantByMatchIdAndStatusIn(application.getMatch().getId(),
						List.of(MatchApplicationStatus.CONFIRMED))
				: List.of();
		return MatchApplicationDetailResponse.of(application, confirmedApplications, clock.getZone());
	}

	@Transactional
	public MatchApplicationCancelResponse cancel(Long userId, Long applicationId, MatchApplicationCancelReason reason) {
		MatchApplication application = matchApplicationRepository.findByIdAndApplicantIdForUpdate(applicationId, userId)
				.orElseThrow(() -> new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_FOUND));
		try {
			application.cancel(reason, now());
		} catch (MatchApplicationNotCancelableException failure) {
			throw new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_CANCELABLE, failure);
		}
		return MatchApplicationCancelResponse.from(application);
	}

	@Transactional(readOnly = true)
	public HostMatchApplicationListResponse getHostApplications(Long userId, Long matchId) {
		Match match = matchRepository.findDetailById(matchId)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_NOT_FOUND));
		validateHost(match.getHost().getId(), userId);
		return HostMatchApplicationListResponse.of(match,
				matchApplicationRepository.findAllWithApplicantByMatchIdAndStatusIn(matchId,
						MatchApplicationStatus.ACTIVE), clock.getZone());
	}

	@Transactional
	public MatchApplicationAcceptResponse accept(Long userId, Long matchId, Long applicationId) {
		validateHost(findHostId(matchId), userId);
		Match match = matchRepository.findByIdForUpdate(matchId)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_NOT_FOUND));
		MatchApplication application = findPendingApplicationForUpdate(matchId, applicationId);
		LocalDateTime now = now();
		validateRecruiting(match, now);
		int recruitCount = application.getApplicantGender() == Gender.MALE
				? match.getMaleRecruitCount() : match.getFemaleRecruitCount();
		if (matchApplicationRepository.countByMatchIdAndApplicantGenderAndStatusIn(matchId,
				application.getApplicantGender(), MatchApplicationStatus.SEAT_OCCUPYING) >= recruitCount) {
			throw new GeneralException(MatchApplicationErrorStatus.SEAT_UNAVAILABLE);
		}
		application.accept(now, paymentDueAt(now, MatchApplicationEligibility.finalDeadline(match)));
		return MatchApplicationAcceptResponse.from(application);
	}

	@Transactional
	public MatchApplicationRejectResponse reject(Long userId, Long matchId, Long applicationId,
			MatchApplicationRejectReason reason) {
		validateHost(findHostId(matchId), userId);
		MatchApplication application = findPendingApplicationForUpdate(matchId, applicationId);
		application.reject(reason);
		return MatchApplicationRejectResponse.from(application);
	}

	private Long findHostId(Long matchId) {
		return matchRepository.findHostIdById(matchId)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_NOT_FOUND));
	}

	private void validateHost(Long hostId, Long userId) {
		if (!hostId.equals(userId)) {
			throw new GeneralException(MatchApplicationErrorStatus.NOT_MATCH_HOST);
		}
	}

	private MatchApplication findPendingApplicationForUpdate(Long matchId, Long applicationId) {
		MatchApplication application = matchApplicationRepository.findByIdAndMatchIdForUpdate(applicationId, matchId)
				.orElseThrow(() -> new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_FOUND));
		application.validatePending();
		return application;
	}

	private LocalDateTime paymentDueAt(LocalDateTime acceptedAt, LocalDateTime finalDeadline) {
		LocalDateTime dueAt = acceptedAt.plus(PAYMENT_DUE_DURATION);
		return dueAt.isBefore(finalDeadline) ? dueAt : finalDeadline;
	}

	private void validateRecruiting(Match match, LocalDateTime now) {
		if (match.getStatus() != MatchStatus.RECRUITING) {
			throw new GeneralException(MatchErrorStatus.MATCH_NOT_RECRUITING);
		}
		if (!MatchApplicationEligibility.isWithinRecruitmentWindow(match, now)) {
			throw new GeneralException(MatchErrorStatus.MATCH_RECRUITMENT_CLOSED);
		}
	}

	private PageResponse<MyMatchApplicationListItemResponse> listResponse(Page<MatchApplication> applications) {
		Map<Long, Long> counts = occupiedSeatCounts(applications.getContent());
		return PageResponse.from(applications.map(
				application -> MyMatchApplicationListItemResponse.of(application, counts, clock.getZone())));
	}

	private Map<Long, Long> occupiedSeatCounts(List<MatchApplication> applications) {
		if (applications.isEmpty()) {
			return Map.of();
		}
		List<Long> matchIds = applications.stream().map(application -> application.getMatch().getId()).toList();
		return matchApplicationRepository.countByMatchIdsAndStatuses(matchIds,
						MatchApplicationStatus.SEAT_OCCUPYING)
				.stream()
				.collect(Collectors.toMap(MatchApplicationCount::getMatchId, MatchApplicationCount::getCount));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
