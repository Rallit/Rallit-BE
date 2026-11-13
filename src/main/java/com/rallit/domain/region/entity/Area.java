package com.rallit.domain.region.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Area {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @Column(nullable = false, unique = true, length = 5)
  private String code;

  @Column(nullable = false, length = 40)
  private String name;
}
