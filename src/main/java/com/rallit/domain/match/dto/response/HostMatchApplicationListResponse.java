package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rallit.domain.court.dto.ReservationEvidenceResponse;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

public record HostMatchApplicationListResponse(MatchInfo match, RecruitmentInfo recruitment,
		List<PendingApplicationInfo> pendingApplications, List<ParticipantInfo> participants) {

	public static HostMatchApplicationListResponse of(Match match, List<MatchApplication> activeApplications,
			YearMonth currentMonth) {
		List<MatchApplication> occupying = activeApplications.stream()
				.filter(application -> MatchApplicationStatus.SEAT_OCCUPYING.contains(application.getStatus()))
				.toList();
		List<PendingApplicationInfo> pendingApplications = activeApplications.stream()
				.filter(application -> application.getStatus() == MatchApplicationStatus.PENDING)
				.map(application -> PendingApplicationInfo.from(application, currentMonth))
				.toList();
		List<ParticipantInfo> participants = Stream.concat(
				Stream.of(ParticipantInfo.host(match.getHost(), currentMonth)),
				occupying.stream().map(application -> ParticipantInfo.applicant(application, currentMonth))).toList();
		return new HostMatchApplicationListResponse(MatchInfo.from(match),
				RecruitmentInfo.of(match, occupying, pendingApplications.size()), pendingApplications, participants);
	}

	private static String profileImageUrlOf(User user) {
		return user.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(user.getId());
	}

	public record MatchInfo(Long matchId, MatchStatus status, List<MatchType> matchTypes, Instant startsAt,
			Instant endsAt, Instant recruitmentDeadline, Integer participationFee, String courtName,
			@Schema(description = "코트 번호", example = "3") String courtNumber,
			@Schema(description = "코트 예약 증빙 검수 현황. 원본 증빙 이미지는 포함하지 않습니다.")
			ReservationEvidenceResponse evidence) {

		private static MatchInfo from(Match match) {
			CourtSlot courtSlot = match.getCourtSlot();
			return new MatchInfo(match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
					courtSlot.getStartsAt(), courtSlot.getEndsAt(), match.getRecruitmentDeadline(),
					match.getParticipationFee(), courtSlot.getCourt().getName(), courtSlot.getCourtNumber(),
					ReservationEvidenceResponse.from(courtSlot));
		}
	}

	public record RecruitmentInfo(
			@Schema(description = "모집 현황 분모(전체 인원)", example = "4") int capacity,
			@Schema(description = "모집 현황 분자(현재 인원)", example = "2") int currentParticipantCount,
			@Schema(description = "새 신청 수", example = "3") int pendingCount,
			@Schema(description = "남성 자리") SeatInfo male,
			@Schema(description = "여성 자리") SeatInfo female,
			@Schema(description = "모든 자리가 찼는지 여부") boolean recruitmentFilled) {

		private static RecruitmentInfo of(Match match, List<MatchApplication> occupying, int pendingCount) {
			SeatInfo male = SeatInfo.of(match.getMaleRecruitCount(), occupying, Gender.MALE);
			SeatInfo female = SeatInfo.of(match.getFemaleRecruitCount(), occupying, Gender.FEMALE);
			return new RecruitmentInfo(1 + male.recruitCount() + female.recruitCount(),
					1 + occupying.size(), pendingCount, male, female,
					male.remainingCount() == 0 && female.remainingCount() == 0);
		}
	}

	public record SeatInfo(@Schema(description = "모집 인원") int recruitCount,
			@Schema(description = "수락된 인원") int occupiedCount,
			@Schema(description = "남은 자리") int remainingCount) {

		private static SeatInfo of(int recruitCount, List<MatchApplication> occupying, Gender gender) {
			int occupiedCount = (int) occupying.stream()
					.filter(application -> application.getApplicantGender() == gender)
					.count();
			return new SeatInfo(recruitCount, occupiedCount, Math.max(0, recruitCount - occupiedCount));
		}
	}

	public record UserInfo(Long userId, String nickname,
			@Schema(description = ProfileImageResponse.URL_DESCRIPTION + " 등록된 사진이 없으면 null입니다.",
					types = {"string", "null"}) String profileImageUrl,
			Gender gender, BigDecimal ntrp, AgeGroup ageGroup,
			@Schema(description = "만 구력(년). 1년 미만은 0, 시작 연월이 없으면 null",
					types = {"integer", "null"}) Integer experienceYears) {

		private static UserInfo of(User user, Gender gender, YearMonth currentMonth) {
			return new UserInfo(user.getId(), user.getNickname(), profileImageUrlOf(user), gender, user.getNtrp(),
					user.getAgeGroup(), user.experienceYears(currentMonth));
		}
	}

	public record PendingApplicationInfo(Long applicationId, Instant appliedAt, UserInfo applicant) {

		private static PendingApplicationInfo from(MatchApplication application, YearMonth currentMonth) {
			return new PendingApplicationInfo(application.getId(), application.getCreatedAt(),
					UserInfo.of(application.getApplicant(), application.getApplicantGender(), currentMonth));
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ParticipantInfo(
			@Schema(description = "호스트 본인이면 true. 이때 applicationId, status 등 신청 필드는 없습니다.") boolean host,
			Long applicationId,
			@Schema(description = "PAYMENT_PENDING 입금 대기 중, CONFIRMED 입금 완료",
					allowableValues = {"PAYMENT_PENDING", "CONFIRMED"}) MatchApplicationStatus status,
			Instant acceptedAt,
			@Schema(description = "입금 기한") Instant paymentDueAt,
			UserInfo user) {

		private static ParticipantInfo host(User host, YearMonth currentMonth) {
			return new ParticipantInfo(true, null, null, null, null,
					UserInfo.of(host, host.getGender(), currentMonth));
		}

		private static ParticipantInfo applicant(MatchApplication application, YearMonth currentMonth) {
			return new ParticipantInfo(false, application.getId(), application.getStatus(),
					application.getAcceptedAt(), application.getPaymentDueAt(),
					UserInfo.of(application.getApplicant(), application.getApplicantGender(), currentMonth));
		}
	}
}
