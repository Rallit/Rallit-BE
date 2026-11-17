package com.rallit.domain.court.dto;

import java.util.ArrayList;
import java.util.List;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.enums.CourtFacilityType;
import io.swagger.v3.oas.annotations.media.Schema;

public record CourtFacilityResponse(CourtFacilityType type,
		@Schema(description = "시설별 이용 안내. 등록된 설명이 없으면 null", types = {"string", "null"}) String note) {

	public static List<CourtFacilityResponse> from(Court court) {
		var facilities = new ArrayList<CourtFacilityResponse>(2);
		if (court.isHasParking()) {
			facilities.add(new CourtFacilityResponse(CourtFacilityType.PARKING, court.getParkingNote()));
		}
		if (court.isHasShower()) {
			facilities.add(new CourtFacilityResponse(CourtFacilityType.SHOWER, court.getShowerNote()));
		}
		return List.copyOf(facilities);
	}
}
