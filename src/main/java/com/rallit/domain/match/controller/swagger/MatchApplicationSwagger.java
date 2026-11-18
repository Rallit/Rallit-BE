package com.rallit.domain.match.controller.swagger;

import com.rallit.domain.match.dto.request.MatchApplicationCancelRequest;
import com.rallit.domain.match.dto.request.MatchApplicationCreateRequest;
import com.rallit.domain.match.dto.response.MatchApplicationCancelResponse;
import com.rallit.domain.match.dto.response.MatchApplicationCreateResponse;
import com.rallit.domain.match.dto.response.MatchApplicationDetailResponse;
import com.rallit.domain.match.dto.response.MyMatchApplicationListItemResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;

@Tag(name = "매치 참가 신청")
public interface MatchApplicationSwagger {

	@Operation(
			summary = "매치 참가 신청",
			description = "모집 중인 매치에 참가를 신청합니다. 신청은 호스트 수락 전 PENDING 상태이며 좌석을 점유하지 않습니다. "
					+ "모집 마감이나 최종 마감(경기 시작 1시간 전)이 지나면 신청할 수 없습니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	ResponseEntity<ApiResponse<MatchApplicationCreateResponse>> apply(
			UserDetails principal,
			Long matchId,
			@Valid MatchApplicationCreateRequest request);

	@Operation(
			summary = "매치 현황 조회",
			description = "진행 중인 참가 신청(PENDING, PAYMENT_PENDING, CONFIRMED)을 경기 시작 일시 오름차순으로 조회합니다. "
					+ "date가 없으면 오늘 이후 경기를, date가 있으면 해당 날짜의 경기만 조회합니다. "
					+ "startsAt과 endsAt에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyScheduledApplications(
			UserDetails principal,
			LocalDate date,
			@Min(0) @Max(10_000) int page,
			@Min(1) @Max(50) int size);

	@Operation(
			summary = "거절·취소·기한 만료 신청 조회",
			description = "종료된 참가 신청(REJECTED, CANCELED, EXPIRED)을 최근 처리순으로 조회합니다. "
					+ "startsAt과 endsAt에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	ResponseEntity<ApiResponse<PageResponse<MyMatchApplicationListItemResponse>>> getMyClosedApplications(
			UserDetails principal,
			@Min(0) @Max(10_000) int page,
			@Min(1) @Max(50) int size);

	@Operation(
			summary = "참가 신청 상세 조회",
			description = "본인의 참가 신청 상세를 조회합니다. 상태에 따라 값이 있는 필드만 응답합니다. "
					+ "match의 startsAt, endsAt, recruitmentDeadline에는 서울 오프셋(+09:00)이 포함됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	ResponseEntity<ApiResponse<MatchApplicationDetailResponse>> getApplication(
			UserDetails principal,
			Long applicationId);

	@Operation(
			summary = "결제 전 참가 신청 취소",
			description = "결제 전(PENDING, PAYMENT_PENDING) 본인 신청을 취소합니다. 결제 대기 중인 신청을 취소하면 자리가 반환됩니다. "
					+ "이미 취소된 신청은 기존 취소 정보를 그대로 반환합니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	ResponseEntity<ApiResponse<MatchApplicationCancelResponse>> cancel(
			UserDetails principal,
			Long applicationId,
			MatchApplicationCancelRequest request);
}
