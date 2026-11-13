package com.rallit.domain.region.service;

import com.rallit.domain.region.dto.RegionResponse;
import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.domain.region.repository.CityRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {
  private final CityRepository cities;
  private final AreaRepository areas;

  public List<RegionResponse> getCities() {
    return cities.findAllByOrderByCode().stream()
        .map(city -> new RegionResponse(city.getCode(), city.getName()))
        .toList();
  }

  public List<RegionResponse> getAreas(String cityCode) {
    if (!cities.existsByCode(cityCode)) {
      throw new GeneralException(ErrorStatus._NOT_FOUND);
    }
    return areas.findAllByCityCodeOrderByCode(cityCode).stream()
        .map(area -> new RegionResponse(area.getCode(), area.getName()))
        .toList();
  }
}
