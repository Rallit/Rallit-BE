package com.rallit.domain.match.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.rallit.domain.match.dto.request.MatchDate;
import com.rallit.domain.match.dto.request.MatchSearchRequest;
import com.rallit.domain.match.dto.response.MatchRequestCreatedResponse;
import com.rallit.domain.match.dto.response.MatchRequestResponse;
import com.rallit.domain.match.dto.response.MatchSearchResponse;
import com.rallit.domain.match.entity.MatchRequest;
import com.rallit.domain.match.entity.MatchRequestConditions;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchRequestRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.PreferredMatchType;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MatchRequestService {

	private static final int MAX_ACTIVE_REQUESTS = 3;
	private static final int SELECTABLE_DAYS = 14;

	private final MatchRequestRepository requests;
	private final UserRepository users;
	private final MatchRecommendationService recommendations;
	private final Clock clock;
	private final PlatformTransactionManager transactions;

	public MatchRequestCreatedResponse create(Long userId, MatchSearchRequest request) {
		requireOnboarding(users.findById(userId));
		var dates = request.dates().stream().map(MatchDate::parse).distinct().sorted().toList();
		var startTime = LocalTime.parse(request.startTimeFrom());
		validateSchedule(dates, startTime, now());
		if (request.sameGenderOnly() && request.matchTypes().contains(PreferredMatchType.MIXED_DOUBLES)) {
			throw new GeneralException(MatchErrorStatus.MATCH_REQUEST_INVALID_PREFERENCES);
		}

		var conditions = new MatchRequestConditions(dates.stream().map(Object::toString).toList(),
				request.areaCodes().stream().distinct().toList(), request.startTimeFrom(), request.startTimeTo(),
				request.matchTypes().stream().distinct().toList(), request.minNtrp(), request.maxNtrp(),
				request.sameGenderOnly());
		var preview = recommendations.search(userId, toSearchRequest(conditions), 0, 0, 1);
		var saved = new TransactionTemplate(transactions).execute(transaction -> {
			var owner = requireOnboarding(users.findByIdForUpdate(userId));
			var now = now();
			validateSchedule(dates, startTime, now);
			if (requests.countByOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(userId, now) >= MAX_ACTIVE_REQUESTS) {
				throw new GeneralException(MatchErrorStatus.MATCH_REQUEST_LIMIT_REACHED);
			}
			return requests.save(new MatchRequest(owner, conditions, dates.getLast().plusDays(1).atStartOfDay()));
		});
		return new MatchRequestCreatedResponse(toResponse(saved), preview.exactMatches().totalElements(),
				preview.hasResults());
	}

	@Transactional(readOnly = true)
	public List<MatchRequestResponse> list(Long userId) {
		requireOnboarding(users.findById(userId));
		return requests.findAllByOwnerIdAndDeletedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(userId, now())
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public MatchRequestResponse detail(Long userId, Long requestId) {
		requireOnboarding(users.findById(userId));
		return toResponse(activeRequest(userId, requestId, now()));
	}

	@Transactional(readOnly = true)
	public MatchSearchResponse results(Long userId, Long requestId, int exactPage, int similarPage, int size) {
		requireOnboarding(users.findById(userId));
		var request = activeRequest(userId, requestId, now());
		return recommendations.search(userId, toSearchRequest(request.getConditions()), exactPage, similarPage, size);
	}

	@Transactional
	public void delete(Long userId, Long requestId) {
		requireOnboarding(users.findByIdForUpdate(userId));
		var now = now();
		activeRequest(userId, requestId, now).delete(now);
	}

	private static void validateSchedule(List<LocalDate> dates, LocalTime startTime, LocalDateTime now) {
		var today = now.toLocalDate();
		if (dates.getFirst().isBefore(today) || !dates.getLast().isBefore(today.plusDays(SELECTABLE_DAYS))) {
			throw new GeneralException(MatchErrorStatus.MATCH_REQUEST_INVALID_DATE);
		}
		if (dates.getFirst().equals(today) && !today.atTime(startTime).isAfter(now)) {
			throw new GeneralException(MatchErrorStatus.MATCH_REQUEST_PAST_START_TIME);
		}
	}

	private MatchRequest activeRequest(Long userId, Long requestId, LocalDateTime now) {
		return requests.findByIdAndOwnerIdAndDeletedAtIsNullAndExpiresAtAfter(requestId, userId, now)
				.orElseThrow(() -> new GeneralException(MatchErrorStatus.MATCH_REQUEST_NOT_FOUND));
	}

	private static User requireOnboarding(Optional<User> result) {
		var user = result.orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
		if (!user.isOnboardingCompleted()) {
			throw new GeneralException(MatchErrorStatus.MATCH_REQUEST_ONBOARDING_REQUIRED);
		}
		return user;
	}

	private static MatchSearchRequest toSearchRequest(MatchRequestConditions conditions) {
		return new MatchSearchRequest(conditions.dates(), conditions.areaCodes(), conditions.startTimeFrom(),
				conditions.startTimeTo(), conditions.matchTypes(), conditions.minNtrp(), conditions.maxNtrp(),
				conditions.sameGenderOnly());
	}

	private MatchRequestResponse toResponse(MatchRequest request) {
		return MatchRequestResponse.from(request, clock.getZone());
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
