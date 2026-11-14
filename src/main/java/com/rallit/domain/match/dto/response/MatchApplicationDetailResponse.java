package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.entity.MatchApplication;
import com.rallit.domain.match.enums.MatchApplicationCancelReason;
import com.rallit.domain.match.enums.MatchApplicationStatus;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchApplicationDetailResponse(Long applicationId, MatchApplicationStatus status,
		LocalDateTime appliedAt, LocalDateTime refundPolicyAgreedAt, LocalDateTime acceptedAt, MatchInfo match,
		HostInfo host, PaymentInfo payment, List<ParticipantInfo> participants, CancellationInfo cancellation) {

	public static MatchApplicationDetailResponse of(MatchApplication application,
			List<MatchApplication> confirmedApplications) {
		Match match = application.getMatch();
		MatchApplicationStatus status = application.getStatus();
		return new MatchApplicationDetailResponse(application.getId(), status, application.getCreatedAt(),
				application.getRefundPolicyAgreedAt(), application.getAcceptedAt(), MatchInfo.from(match),
				HostInfo.from(match.getHost()),
				status == MatchApplicationStatus.PAYMENT_PENDING ? PaymentInfo.from(application) : null,
				status == MatchApplicationStatus.CONFIRMED ? participants(match.getHost(), confirmedApplications) : null,
				status == MatchApplicationStatus.CANCELED ? CancellationInfo.from(application) : null);
	}

	private static List<ParticipantInfo> participants(User host, List<MatchApplication> confirmedApplications) {
		return Stream.concat(Stream.of(ParticipantInfo.host(host)),
				confirmedApplications.stream().map(ParticipantInfo::applicant)).toList();
	}

	private static String profileImageUrlOf(User user) {
		return user.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(user.getId());
	}

	public record MatchInfo(Long matchId, MatchStatus status, List<MatchType> matchTypes, LocalDateTime startsAt,
			LocalDateTime endsAt, Integer participationFee, LocalDateTime recruitmentDeadline,
			Integer maleRecruitCount, Integer femaleRecruitCount, CourtInfo court) {

		private static MatchInfo from(Match match) {
			CourtSlot courtSlot = match.getCourtSlot();
			return new MatchInfo(match.getId(), match.getStatus(), match.getMatchTypes().stream().sorted().toList(),
					courtSlot.getStartsAt(), courtSlot.getEndsAt(), match.getParticipationFee(),
					match.getRecruitmentDeadline(), match.getMaleRecruitCount(), match.getFemaleRecruitCount(),
					CourtInfo.from(courtSlot));
		}
	}

	public record CourtInfo(Long courtId, String name, String address, BigDecimal latitude, BigDecimal longitude,
			String courtNumber, boolean hasParking, boolean hasShower, String facilityNotes) {

		private static CourtInfo from(CourtSlot courtSlot) {
			Court court = courtSlot.getCourt();
			return new CourtInfo(court.getId(), court.getName(), court.getAddress(), court.getLatitude(),
					court.getLongitude(), courtSlot.getCourtNumber(), court.isHasParking(), court.isHasShower(),
					court.getFacilityNotes());
		}
	}

	public record HostInfo(Long userId, String nickname, String profileImageUrl, BigDecimal ntrp) {

		private static HostInfo from(User host) {
			return new HostInfo(host.getId(), host.getNickname(), profileImageUrlOf(host), host.getNtrp());
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PaymentInfo(Integer amount, LocalDateTime paymentDueAt) {

		private static PaymentInfo from(MatchApplication application) {
			return new PaymentInfo(application.getMatch().getParticipationFee(), application.getPaymentDueAt());
		}
	}

	public record ParticipantInfo(Long userId, String nickname, String profileImageUrl, Gender gender,
			BigDecimal ntrp, boolean host) {

		private static ParticipantInfo host(User host) {
			return new ParticipantInfo(host.getId(), host.getNickname(), profileImageUrlOf(host), host.getGender(),
					host.getNtrp(), true);
		}

		private static ParticipantInfo applicant(MatchApplication application) {
			User applicant = application.getApplicant();
			return new ParticipantInfo(applicant.getId(), applicant.getNickname(), profileImageUrlOf(applicant),
					application.getApplicantGender(), applicant.getNtrp(), false);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CancellationInfo(MatchApplicationCancelReason reason, LocalDateTime canceledAt) {

		private static CancellationInfo from(MatchApplication application) {
			return new CancellationInfo(application.getCancelReason(), application.getCanceledAt());
		}
	}
}
