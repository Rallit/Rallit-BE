package com.rallit.domain.region.controller;

import com.rallit.domain.region.controller.swagger.RegionSwagger;
import com.rallit.domain.region.dto.RegionResponse;
import com.rallit.domain.region.service.RegionService;
import com.rallit.global.apiPayload.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cities")
public class RegionController implements RegionSwagger {
  private final RegionService regions;

  @Override
  @GetMapping
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getCities() {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getCities()));
  }

  @Override
  @GetMapping("/{cityCode}/areas")
  public ResponseEntity<ApiResponse<List<RegionResponse>>> getAreas(
      @PathVariable String cityCode) {
    return ResponseEntity.ok(ApiResponse.onSuccess(regions.getAreas(cityCode)));
  }
}
