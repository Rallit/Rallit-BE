package com.rallit.domain.match.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.exception.MatchApplicationErrorStatus;
import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.domain.match.repository.MatchApplicationRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.global.apiPayload.code.BaseErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MatchApplicationEligibility {

	private final MatchApplicationRepository applications;

	Optional<BaseErrorCode> blockReason(Match match, User applicant, LocalDateTime now) {
		if (match.getStatus() != MatchStatus.RECRUITING) {
			return Optional.of(MatchErrorStatus.MATCH_NOT_RECRUITING);
		}
		if (!isWithinRecruitmentWindow(match, now)) {
			return Optional.of(MatchErrorStatus.MATCH_RECRUITMENT_CLOSED);
		}
		if (match.getHost().getId().equals(applicant.getId())) {
			return Optional.of(MatchApplicationErrorStatus.HOST_NOT_ALLOWED);
		}
		int recruitCount = applicant.getGender() == Gender.MALE
				? match.getMaleRecruitCount() : match.getFemaleRecruitCount();
		if (recruitCount <= 0) {
			return Optional.of(MatchApplicationErrorStatus.GENDER_NOT_RECRUITED);
		}
		if (applications.countByMatchIdAndApplicantGenderAndStatus(match.getId(),
				applicant.getGender(), MatchApplicationStatus.CONFIRMED) >= recruitCount) {
			return Optional.of(MatchApplicationErrorStatus.RECRUITMENT_FULL);
		}
		if (applications.existsActiveApplication(match.getId(), applicant.getId())) {
			return Optional.of(MatchApplicationErrorStatus.APPLICATION_DUPLICATED);
		}
		return Optional.empty();
	}

	static boolean isWithinRecruitmentWindow(Match match, LocalDateTime now) {
		return now.isBefore(match.getRecruitmentDeadline()) && now.isBefore(match.getCourtSlot().getStartsAt());
	}
}
