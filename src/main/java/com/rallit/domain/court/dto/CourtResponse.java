package com.rallit.domain.court.dto;

import java.math.BigDecimal;

import com.rallit.domain.court.entity.Court;
import io.swagger.v3.oas.annotations.media.Schema;

public record CourtResponse(
		@Schema(description = "매치 개설 요청에 사용할 코트 ID", example = "12") Long courtId,
		@Schema(description = "카카오 장소 ID. 운영자가 직접 등록한 코트는 null", types = {"string", "null"})
		String kakaoPlaceId,
		String name, String address, BigDecimal latitude, BigDecimal longitude,
		@Schema(description = "코트가 속한 자치구 코드", types = {"string", "null"}) String areaCode,
		@Schema(description = "코트가 속한 자치구 이름", types = {"string", "null"}) String areaName) {

	public static CourtResponse from(Court court) {
		var area = court.getArea();
		return new CourtResponse(court.getId(), court.getKakaoPlaceId(), court.getName(), court.getAddress(),
				court.getLatitude(), court.getLongitude(), area == null ? null : area.getCode(),
				area == null ? null : area.getName());
	}
}
