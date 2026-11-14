package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.domain.user.enums.ExperiencePeriod;
import com.rallit.global.apiPayload.code.BaseErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchDetailResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		OffsetDateTime startsAt, OffsetDateTime endsAt, Integer participationFee,
		@Schema(description = "모집 NTRP 하한. 미설정이면 null", types = {"number", "null"}) BigDecimal minNtrp,
		@Schema(description = "모집 NTRP 상한. 미설정이면 null", types = {"number", "null"}) BigDecimal maxNtrp,
		long currentParticipantCount, int capacity, OffsetDateTime recruitmentDeadline,
		@Schema(description = "모집 마감과 경기 시작이 모두 지나지 않았는지 여부") boolean recruitmentOpen,
		@Schema(description = "조회 시점에 현재 로그인 회원이 신청 가능한지 여부") boolean canApply,
		@Schema(description = "신청 가능하면 null. 신청 불가 사유는 MATCH_RECRUITMENT_CLOSED, HOST_NOT_ALLOWED, "
				+ "GENDER_NOT_RECRUITED, RECRUITMENT_FULL, APPLICATION_DUPLICATED 중 하나입니다.",
				types = {"string", "null"}) String applicationBlockReason,
		Integer maleRecruitCount, Integer femaleRecruitCount,
		List<AgeGroup> preferredAgeGroups, CourtInfo court, HostInfo host) {

	public static MatchDetailResponse from(Match match, long occupiedSeats, ZoneId zone, boolean recruitmentOpen,
			BaseErrorCode blockReason) {
		var card = MatchListItemResponse.from(match, occupiedSeats, zone);
		var slot = match.getCourtSlot();
		var court = slot.getCourt();
		var host = match.getHost();
		return new MatchDetailResponse(card.matchId(), card.matchStatus(), card.matchTypes(), card.startsAt(), card.endsAt(),
				card.participationFee(), card.minNtrp(), card.maxNtrp(), card.currentParticipantCount(), card.capacity(),
				match.getRecruitmentDeadline().atZone(zone).toOffsetDateTime(), recruitmentOpen,
				blockReason == null, blockReason == null ? null : blockReason.getReason().getCode(),
				match.getMaleRecruitCount(), match.getFemaleRecruitCount(),
				match.getPreferredAgeGroups().stream().sorted().toList(),
				new CourtInfo(court.getId(), court.getName(), court.getAddress(), slot.getCourtNumber(),
						court.getLatitude(), court.getLongitude(), court.isHasParking(), court.isHasShower(),
						court.getFacilityNotes()),
				new HostInfo(host.getId(), host.getNickname(), host.getExperiencePeriod(),
						host.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(host.getId())));
	}

	public record CourtInfo(Long courtId, String name, String address, String courtNumber, BigDecimal latitude,
			BigDecimal longitude, boolean hasParking, boolean hasShower,
			@Schema(types = {"string", "null"}) String facilityNotes) {
	}

	public record HostInfo(Long userId, String nickname,
			@Schema(types = {"string", "null"}) ExperiencePeriod experiencePeriod,
			@Schema(description = "Bearer 인증이 필요한 API 상대 경로. 등록된 사진이 없으면 null",
					types = {"string", "null"}) String profileImageUrl) {
	}
}
