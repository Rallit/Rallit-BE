package com.rallit.domain.match.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.rallit.domain.match.dto.response.MatchDetailResponse;
import com.rallit.domain.match.dto.response.MatchListResponse;
import com.rallit.domain.match.enums.MatchType;
import com.rallit.domain.match.service.MatchDiscoveryService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
@Tag(name = "매치 조회")
@SecurityRequirement(name = "bearerAuth")
public class MatchDiscoveryController {

	private final MatchDiscoveryService discovery;

	@GetMapping
	@Operation(summary = "모집 중 매치 목록",
			description = "서울 시간 기준 날짜와 시간대를 사용합니다. 시간대는 경기 전체를 포함해야 합니다. "
					+ "복수 경기 방식 중 하나 이상이 일치하면 포함하며, 경기 시작 시각과 매치 ID 순으로 정렬합니다.")
	public ResponseEntity<ApiResponse<MatchListResponse>> list(@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeFrom,
			@RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTimeTo,
			@RequestParam(required = false) @Size(max = 5) List<MatchType> matchTypes,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(
				discovery.list(date, startTimeFrom, startTimeTo, matchTypes, page, size)));
	}

	@GetMapping("/{matchId}")
	@Operation(summary = "모집 매치 상세",
			description = "matchId를 참가 신청에 그대로 사용합니다. canApply는 조회 시점의 신청 가능 여부입니다.")
	public ResponseEntity<ApiResponse<MatchDetailResponse>> detail(@AuthenticationPrincipal UserDetails principal,
			@PathVariable @Positive Long matchId) {
		requireOnboarding(principal);
		return ResponseEntity.ok(ApiResponse.onSuccess(discovery.detail(matchId, principal.userId())));
	}

	private void requireOnboarding(UserDetails principal) {
		if (!principal.onboardingCompleted()) {
			throw new GeneralException(ErrorStatus._FORBIDDEN);
		}
	}
}
