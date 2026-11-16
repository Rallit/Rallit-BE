package com.rallit.domain.match.entity;

import java.math.BigDecimal;
import java.util.List;

import com.rallit.domain.user.enums.PreferredMatchType;

public record MatchRequestConditions(List<String> dates, List<String> areaCodes, String startTimeFrom,
		String startTimeTo, List<PreferredMatchType> matchTypes, BigDecimal minNtrp, BigDecimal maxNtrp,
		boolean sameGenderOnly) {

	public MatchRequestConditions {
		dates = List.copyOf(dates);
		areaCodes = List.copyOf(areaCodes);
		matchTypes = List.copyOf(matchTypes);
	}
}
