package com.rallit.domain.match.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import com.rallit.domain.court.dto.CourtFacilityResponse;
import com.rallit.domain.court.dto.CourtThumbnailResponse;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.match.enums.MatchStatus;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.user.dto.ProfileImageResponse;
import com.rallit.domain.user.enums.AgeGroup;
import com.rallit.global.apiPayload.code.BaseErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record MatchDetailResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		Instant startsAt, Instant endsAt, Integer participationFee,
		@Schema(description = "모집 NTRP 하한. 미설정이면 null", types = {"number", "null"}) BigDecimal minNtrp,
		@Schema(description = "모집 NTRP 상한. 미설정이면 null", types = {"number", "null"}) BigDecimal maxNtrp,
		long currentParticipantCount, int capacity, Instant recruitmentDeadline,
		@Schema(description = "모집 마감과 최종 마감(경기 시작 1시간 전)이 모두 지나지 않았는지 여부") boolean recruitmentOpen,
		@Schema(description = "조회 시점에 현재 로그인 회원이 신청 가능한지 여부") boolean canApply,
		@Schema(description = "신청 가능하면 null. 신청 불가 사유는 MATCH_RECRUITMENT_CLOSED, HOST_NOT_ALLOWED, "
				+ "GENDER_NOT_RECRUITED, RECRUITMENT_FULL, APPLICATION_DUPLICATED, APPLICATION_REJECTED 중 하나입니다.",
				types = {"string", "null"}) String applicationBlockReason,
		Integer maleRecruitCount, Integer femaleRecruitCount,
		List<AgeGroup> preferredAgeGroups, CourtInfo court, HostInfo host) {

	public static MatchDetailResponse from(Match match, long occupiedSeats, boolean recruitmentOpen,
			BaseErrorCode blockReason, YearMonth currentMonth) {
		var card = MatchListItemResponse.from(match, occupiedSeats);
		var slot = match.getCourtSlot();
		var court = slot.getCourt();
		var host = match.getHost();
		return new MatchDetailResponse(card.matchId(), card.matchStatus(), card.matchTypes(), card.startsAt(), card.endsAt(),
				card.participationFee(), card.minNtrp(), card.maxNtrp(), card.currentParticipantCount(), card.capacity(),
				match.getRecruitmentDeadline(), recruitmentOpen,
				blockReason == null, blockReason == null ? null : blockReason.getReason().getCode(),
				match.getMaleRecruitCount(), match.getFemaleRecruitCount(),
				match.getPreferredAgeGroups().stream().sorted().toList(),
				new CourtInfo(court.getId(), court.getName(), court.getAddress(), slot.getCourtNumber(),
						court.getLatitude(), court.getLongitude(), court.isHasParking(), court.isHasShower(),
						court.getFacilityNotes(), CourtFacilityResponse.from(court), card.courtThumbnailUrl()),
				new HostInfo(host.getId(), host.getNickname(), host.experienceYears(currentMonth),
						host.getProfileImageKey() == null ? null : ProfileImageResponse.urlFor(host.getId())));
	}

	public record CourtInfo(Long courtId, String name, String address, String courtNumber, BigDecimal latitude,
			BigDecimal longitude, boolean hasParking, boolean hasShower,
			@Schema(description = "코트 공통 이용 안내", types = {"string", "null"}) String facilityNotes,
			@Schema(description = "이용 가능한 시설과 항목별 안내. 해당 시설이 없으면 빈 배열")
			List<CourtFacilityResponse> facilities,
			@Schema(description = CourtThumbnailResponse.URL_DESCRIPTION + " 등록된 사진이 없으면 null입니다.",
					types = {"string", "null"}) String thumbnailUrl) {
	}

	public record HostInfo(Long userId, String nickname,
			@Schema(description = "만 구력(년). 1년 미만은 0, 시작 연월이 없으면 null",
					types = {"integer", "null"}) Integer experienceYears,
			@Schema(description = ProfileImageResponse.URL_DESCRIPTION + " 등록된 사진이 없으면 null입니다.",
					types = {"string", "null"}) String profileImageUrl) {
	}
}
