package com.rallit.domain.court.entity;

import java.math.BigDecimal;

import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "court")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Court extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(nullable = false)
	private boolean hasParking;

	@Column(nullable = false)
	private boolean hasShower;

	@Column(columnDefinition = "text")
	private String facilityNotes;

	@Builder
	private Court(String name, String address, BigDecimal latitude, BigDecimal longitude, boolean hasParking,
			boolean hasShower, String facilityNotes) {
		this.name = name;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.hasParking = hasParking;
		this.hasShower = hasShower;
		this.facilityNotes = facilityNotes;
	}
}
