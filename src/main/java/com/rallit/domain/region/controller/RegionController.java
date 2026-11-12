package com.rallit.domain.region.controller;

import com.rallit.domain.region.dto.RegionResponse;
import com.rallit.domain.region.service.RegionService;
import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "활동 지역")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cities")
public class RegionController {
  private final RegionService regions;

  @Operation(summary = "시·도 목록 조회")
  @GetMapping
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getCities() {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getCities()));
  }

  @Operation(summary = "시·도별 자치구 목록 조회")
  @GetMapping("/{cityCode}/areas")
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getAreas(
      @PathVariable @Pattern(regexp = "[0-9]{2}") String cityCode) {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getAreas(cityCode)));
  }
}
