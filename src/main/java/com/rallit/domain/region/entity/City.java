package com.rallit.domain.region.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 2)
  private String code;

  @Column(nullable = false, length = 40)
  private String name;
}
