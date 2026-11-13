package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.rallit.domain.match.dto.response.MatchApplicationCancelResponse;
import com.rallit.domain.match.dto.response.MatchApplicationCreateResponse;
import com.rallit.domain.match.dto.response.MatchApplicationDetailResponse;
import com.rallit.domain.match.dto.response.MyMatchApplicationListResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationConstraintViolations;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.match.repository.MatchApplicationRepository.MatchApplicationCount;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchApplicationService {

	private final MatchApplicationRepository matchApplicationRepository;
	private final MatchRepository matchRepository;
	private final UserRepository userRepository;
	private final Clock clock;

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
		validateApplicable(match, applicant, now);
		if (matchApplicationRepository.existsActiveApplication(matchId, userId)) {
			throw new GeneralException(MatchApplicationErrorStatus.APPLICATION_DUPLICATED);
		}
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
	public MyMatchApplicationListResponse getMyScheduledApplications(Long userId, LocalDate date, int page,
			int size) {
		LocalDateTime from = (date == null ? LocalDate.now(clock) : date).atStartOfDay();
		LocalDateTime to = date == null ? null : from.plusDays(1);
		return listResponse(matchApplicationRepository.findScheduledSliceByApplicantId(userId,
				MatchApplicationStatus.ACTIVE, from, to, PageRequest.of(page, size)));
	}

	@Transactional(readOnly = true)
	public MyMatchApplicationListResponse getMyClosedApplications(Long userId, int page, int size) {
		return listResponse(matchApplicationRepository.findRecentSliceByApplicantId(userId,
				MatchApplicationStatus.CLOSED, PageRequest.of(page, size)));
	}

	@Transactional(readOnly = true)
	public MatchApplicationDetailResponse getApplication(Long userId, Long applicationId) {
		MatchApplication application = matchApplicationRepository.findDetailByIdAndApplicantId(applicationId, userId)
				.orElseThrow(() -> new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_FOUND));
		List<MatchApplication> confirmedApplications = application.getStatus() == MatchApplicationStatus.CONFIRMED
				? matchApplicationRepository.findAllWithApplicantByMatchIdAndStatus(application.getMatch().getId(),
						MatchApplicationStatus.CONFIRMED)
				: List.of();
		return MatchApplicationDetailResponse.of(application, confirmedApplications);
	}

	@Transactional
	public MatchApplicationCancelResponse cancel(Long userId, Long applicationId, MatchApplicationCancelReason reason) {
		MatchApplication application = matchApplicationRepository.findByIdAndApplicantIdForUpdate(applicationId, userId)
				.orElseThrow(() -> new GeneralException(MatchApplicationErrorStatus.APPLICATION_NOT_FOUND));
		application.cancel(reason, now());
		return MatchApplicationCancelResponse.from(application);
	}

	private void validateApplicable(Match match, User applicant, LocalDateTime now) {
		if (match.getStatus() != MatchStatus.RECRUITING) {
			throw new GeneralException(MatchErrorStatus.MATCH_NOT_RECRUITING);
		}
		if (!now.isBefore(match.getRecruitmentDeadline()) || !now.isBefore(match.getCourtSlot().getStartsAt())) {
			throw new GeneralException(MatchErrorStatus.MATCH_RECRUITMENT_CLOSED);
		}
		if (match.getHost().getId().equals(applicant.getId())) {
			throw new GeneralException(MatchApplicationErrorStatus.HOST_NOT_ALLOWED);
		}
		int recruitCount = applicant.getGender() == Gender.MALE
				? match.getMaleRecruitCount() : match.getFemaleRecruitCount();
		if (recruitCount <= 0) {
			throw new GeneralException(MatchApplicationErrorStatus.GENDER_NOT_RECRUITED);
		}
		if (matchApplicationRepository.countByMatchIdAndApplicantGenderAndStatus(match.getId(),
				applicant.getGender(), MatchApplicationStatus.CONFIRMED) >= recruitCount) {
			throw new GeneralException(MatchApplicationErrorStatus.RECRUITMENT_FULL);
		}
	}

	private MyMatchApplicationListResponse listResponse(Slice<MatchApplication> applications) {
		return MyMatchApplicationListResponse.of(applications, occupiedSeatCounts(applications.getContent()));
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
