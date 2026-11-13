package com.rallit.domain.region.repository;

import com.rallit.domain.region.entity.City;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
  List<City> findAllByOrderByCode();

  boolean existsByCode(String code);
}
