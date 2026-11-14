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

public record MatchDetailResponse(Long matchId, MatchStatus matchStatus, List<MatchType> matchTypes,
		OffsetDateTime startsAt, OffsetDateTime endsAt, Integer participationFee, BigDecimal minNtrp, BigDecimal maxNtrp,
		long currentParticipantCount, int capacity, OffsetDateTime recruitmentDeadline, boolean recruitmentOpen,
		boolean canApply, String applicationBlockReason, Integer maleRecruitCount, Integer femaleRecruitCount,
		List<AgeGroup> preferredAgeGroups, CourtInfo court, HostInfo host) {

	public static MatchDetailResponse from(Match match, long occupiedSeats, ZoneId zone, boolean recruitmentOpen,
			BaseErrorCode blockReason) {
		var card = MatchListResponse.Item.from(match, occupiedSeats, zone);
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
			BigDecimal longitude, boolean hasParking, boolean hasShower, String facilityNotes) {
	}

	public record HostInfo(Long userId, String nickname, ExperiencePeriod experiencePeriod, String profileImageUrl) {
	}
}
