package com.rallit.domain.court.controller;

import com.rallit.domain.court.controller.swagger.AdminEvidenceReviewSwagger;
import com.rallit.domain.court.dto.EvidenceRejectRequest;
import com.rallit.domain.court.dto.EvidenceReviewItemResponse;
import com.rallit.domain.court.dto.EvidenceReviewResponse;
import com.rallit.domain.court.enums.EvidenceReviewStatus;
import com.rallit.domain.court.service.EvidenceReviewService;
import com.rallit.domain.image.dto.ImageLinkResponse;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.apiPayload.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/court-slots", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminEvidenceReviewController implements AdminEvidenceReviewSwagger {

	private final EvidenceReviewService reviews;

	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<EvidenceReviewItemResponse>>> list(
			@RequestParam(defaultValue = "PENDING") EvidenceReviewStatus evidenceReviewStatus,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.onSuccess(reviews.list(evidenceReviewStatus, page, size)));
	}

	@Override
	@GetMapping("/{courtSlotId}/evidence/url")
	public ResponseEntity<ApiResponse<ImageLinkResponse>> evidenceImage(@PathVariable Long courtSlotId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(reviews.evidenceImage(courtSlotId)));
	}

	@Override
	@PostMapping("/{courtSlotId}/evidence/approve")
	public ResponseEntity<ApiResponse<EvidenceReviewResponse>> approve(@PathVariable Long courtSlotId) {
		return ResponseEntity.ok(ApiResponse.onSuccess(reviews.approve(courtSlotId)));
	}

	@Override
	@PostMapping(value = "/{courtSlotId}/evidence/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<EvidenceReviewResponse>> reject(@PathVariable Long courtSlotId,
			@RequestBody EvidenceRejectRequest request) {
		return ResponseEntity.ok(ApiResponse.onSuccess(reviews.reject(courtSlotId, request.reason())));
	}
}
