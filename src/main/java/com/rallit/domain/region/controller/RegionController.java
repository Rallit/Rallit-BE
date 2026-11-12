package com.rallit.domain.region.controller;

import com.rallit.domain.region.dto.RegionResponse;
import com.rallit.domain.region.service.RegionService;
import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

  @Operation(summary = "시·도 목록 조회", description = "응답의 `code`를 자치구 목록 조회에 사용합니다. 서울은 `11`입니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getCities() {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getCities()));
  }

  @Operation(
      summary = "시·도별 자치구 목록 조회",
      description = "선택한 자치구의 `code`를 온보딩의 `activityAreaCodes`에 넣습니다. 마포구는 `11440`입니다.")
  @GetMapping("/{cityCode}/areas")
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getAreas(
      @Parameter(description = "시·도 공식 코드", example = "\"11\"")
      @PathVariable @Pattern(regexp = "[0-9]{2}") String cityCode) {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getAreas(cityCode)));
  }
}
