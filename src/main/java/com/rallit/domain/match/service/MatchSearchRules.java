package com.rallit.domain.match.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.entity.CourtSlot;
import com.rallit.domain.match.dto.response.MatchSearchResponse.RelaxedCondition;
import com.rallit.domain.match.entity.Match;
import com.rallit.domain.region.entity.Area;

final class MatchSearchRules {

	private static final double REGION_RADIUS_METERS = 5_000.0;
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;
	private static final Comparator<Match> BY_START_TIME = Comparator.comparing(
			(Match match) -> match.getCourtSlot().getStartsAt()).thenComparing(Match::getId);

	private MatchSearchRules() {
	}

	static SearchResults group(List<Match> candidates, Set<String> areaCodes, List<Area> origins, TimeWindow window) {
		var exact = new ArrayList<Match>();
		var areaOnly = new ArrayList<Candidate>();
		var timeOnly = new ArrayList<Candidate>();
		var areaAndTime = new ArrayList<Candidate>();
		var distanceByCourt = new HashMap<Long, Double>();

		for (Match match : candidates) {
			var slot = match.getCourtSlot();
			var court = slot.getCourt();
			var timeExpansion = window.requiredTimeExpansion(slot);
			boolean exactTime = timeExpansion.isZero();
			if (areaCodes.contains(court.getArea().getCode())) {
				if (exactTime) {
					exact.add(match);
				} else {
					timeOnly.add(new Candidate(match, 0.0, timeExpansion));
				}
				continue;
			}

			double distance = distanceByCourt.computeIfAbsent(court.getId(), id -> nearestOrigin(court, origins));
			if (distance <= REGION_RADIUS_METERS) {
				(exactTime ? areaOnly : areaAndTime).add(new Candidate(match, distance, timeExpansion));
			}
		}
		return selectSimilarMatches(exact, areaOnly, timeOnly, areaAndTime);
	}

	private static SearchResults selectSimilarMatches(List<Match> exact, List<Candidate> areaOnly,
			List<Candidate> timeOnly, List<Candidate> areaAndTime) {
		var byDistance = Comparator.comparingDouble(Candidate::distance);
		var byTime = Comparator.comparing(Candidate::requiredTimeExpansion);
		if (!areaOnly.isEmpty()) {
			return similar(exact, areaOnly, byDistance, List.of(RelaxedCondition.AREA));
		}
		if (!timeOnly.isEmpty()) {
			return similar(exact, timeOnly, byTime, List.of(RelaxedCondition.TIME));
		}
		if (!areaAndTime.isEmpty()) {
			return similar(exact, areaAndTime, byTime.thenComparing(byDistance),
					List.of(RelaxedCondition.AREA, RelaxedCondition.TIME));
		}
		return new SearchResults(exact, List.of(), List.of());
	}

	private static SearchResults similar(List<Match> exact, List<Candidate> candidates, Comparator<Candidate> order,
			List<RelaxedCondition> relaxedConditions) {
		var similar = candidates.stream().sorted(order.thenComparing(Candidate::match, BY_START_TIME))
				.map(Candidate::match).toList();
		return new SearchResults(exact, similar, relaxedConditions);
	}

	private static double nearestOrigin(Court court, List<Area> origins) {
		return origins.stream().filter(area -> area.getCenterLatitude() != null && area.getCenterLongitude() != null)
				.mapToDouble(area -> distanceMeters(area, court)).min().orElse(Double.POSITIVE_INFINITY);
	}

	private static double distanceMeters(Area origin, Court court) {
		double fromLatitude = Math.toRadians(origin.getCenterLatitude().doubleValue());
		double toLatitude = Math.toRadians(court.getLatitude().doubleValue());
		double deltaLongitude = Math.toRadians(court.getLongitude().doubleValue()
				- origin.getCenterLongitude().doubleValue());
		double latitude = Math.sin((toLatitude - fromLatitude) / 2);
		double longitude = Math.sin(deltaLongitude / 2);
		double haversine = latitude * latitude + Math.cos(fromLatitude) * Math.cos(toLatitude) * longitude * longitude;
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
	}

	record TimeWindow(LocalDateTime from, LocalDateTime to, LocalDateTime dayStart, LocalDateTime dayEnd) {

		static TimeWindow of(LocalDate date, LocalTime start, LocalTime end) {
			var dayStart = date.atStartOfDay();
			var dayEnd = date.plusDays(1).atStartOfDay();
			return new TimeWindow(start == null ? dayStart : date.atTime(start),
					end == null ? dayEnd : date.atTime(end), dayStart, dayEnd);
		}

		LocalDateTime expandedFrom() {
			var expanded = from.minusHours(1);
			return expanded.isBefore(dayStart) ? dayStart : expanded;
		}

		LocalDateTime expandedTo() {
			var expanded = to.plusHours(1);
			return expanded.isAfter(dayEnd) ? dayEnd : expanded;
		}

		Duration requiredTimeExpansion(CourtSlot slot) {
			Duration early = slot.getStartsAt().isBefore(from) ? Duration.between(slot.getStartsAt(), from) : Duration.ZERO;
			Duration late = slot.getEndsAt().isAfter(to) ? Duration.between(to, slot.getEndsAt()) : Duration.ZERO;
			return early.plus(late);
		}
	}

	record SearchResults(List<Match> exact, List<Match> similar, List<RelaxedCondition> relaxedConditions) {
	}

	private record Candidate(Match match, double distance, Duration requiredTimeExpansion) {
	}
}
