package com.rallit.domain.region.repository;

import com.rallit.domain.region.entity.Area;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, Long> {
  List<Area> findAllByCityCodeOrderByCode(String cityCode);
}
