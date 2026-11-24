package com.rallit.domain.court.dto;

import java.math.BigDecimal;

import com.rallit.domain.court.client.KakaoLocalClient;
import io.swagger.v3.oas.annotations.media.Schema;

public record CourtPlaceResponse(
		@Schema(description = "카카오 장소 ID. 코트 등록 요청에 그대로 전달합니다.", example = "8152837") String kakaoPlaceId,
		@Schema(example = "장충테니스장") String name,
		@Schema(description = "도로명 주소. 없으면 지번 주소", example = "서울 중구 동호로 241") String address,
		@Schema(example = "37.558651") BigDecimal latitude,
		@Schema(example = "127.004377") BigDecimal longitude,
		@Schema(description = "요청 좌표로부터의 거리(m). 좌표 없이 검색하면 null", types = {"integer", "null"})
		Integer distanceMeters,
		@Schema(description = "카카오 장소 분류", example = "스포츠,레저 > 테니스장", types = {"string", "null"})
		String categoryName) {

	public static CourtPlaceResponse from(KakaoLocalClient.Place place) {
		return new CourtPlaceResponse(place.id(), place.name(), place.address(), place.latitude(), place.longitude(),
				place.distanceMeters(), place.categoryName());
	}
}
