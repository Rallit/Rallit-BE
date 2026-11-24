package com.rallit.domain.court;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.client.KakaoLocalClient;
import com.rallit.domain.court.client.KakaoLocalClient.KeywordSearch;
import com.rallit.domain.court.client.KakaoLocalClient.Place;
import com.rallit.domain.court.client.KakaoLocalClient.PlacePage;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"RALLIT_ENV_FILE=.env.test-disabled", "rallit.kakao.local.requests-per-minute=5"})
@AutoConfigureMockMvc
class CourtSearchIntegrationTest extends IntegrationTestSupport {

	private static final Place JANGCHUNG = new Place("8152837", "장충테니스장", "서울 중구 동호로 241",
			"서울 중구 장충동2가 산 14-49", new BigDecimal("37.558651"), new BigDecimal("127.004377"), 30,
			"스포츠,레저 > 테니스장");

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private TokenSessionService sessions;
	@Autowired private UserRepository users;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private KakaoLocalClient kakao;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM court WHERE kakao_place_id IS NOT NULL");
		userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
	}

	@Test
	void searchesByKeywordWithoutLocation() throws Exception {
		when(kakao.searchKeyword(any())).thenReturn(new PlacePage(List.of(JANGCHUNG), 1, true));

		search(member(), Map.of("keyword", " 장충 "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].kakaoPlaceId").value("8152837"))
				.andExpect(jsonPath("$.result.items[0].name").value("장충테니스장"))
				.andExpect(jsonPath("$.result.items[0].address").value("서울 중구 동호로 241"))
				.andExpect(jsonPath("$.result.items[0].latitude").value(37.558651))
				.andExpect(jsonPath("$.result.items[0].longitude").value(127.004377))
				.andExpect(jsonPath("$.result.page").value(0))
				.andExpect(jsonPath("$.result.hasNext").value(false))
				.andExpect(jsonPath("$.result.totalElements").value(1));

		assertThat(lastSearch()).isEqualTo(new KeywordSearch("장충", null, null, null, false, 1, 15));
	}

	@Test
	void recommendsNearbyTennisCourtsWithinFiveKilometersByDistance() throws Exception {
		when(kakao.searchKeyword(any())).thenReturn(new PlacePage(List.of(JANGCHUNG), 30, false));

		search(member(), Map.of("latitude", "37.5", "longitude", "127.0", "page", "1", "size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[0].distanceMeters").value(30))
				.andExpect(jsonPath("$.result.page").value(1))
				.andExpect(jsonPath("$.result.size").value(10))
				.andExpect(jsonPath("$.result.hasNext").value(true));

		assertThat(lastSearch()).isEqualTo(new KeywordSearch("테니스장", new BigDecimal("37.5"), new BigDecimal("127.0"),
				5000, true, 2, 10));

		search(member(), Map.of("latitude", "37.5", "longitude", "127.0", "page", "44"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.hasNext").value(false));

		search(member(), Map.of("keyword", "테니스장", "latitude", "37.5", "longitude", "127.0", "radius", "1000",
				"sort", "ACCURACY")).andExpect(status().isOk());
		assertThat(lastSearch()).isEqualTo(new KeywordSearch("테니스장", new BigDecimal("37.5"), new BigDecimal("127.0"),
				1000, false, 1, 15));
	}

	@Test
	void returnsAnEmptyListWhenKakaoFindsNothing() throws Exception {
		when(kakao.searchKeyword(any())).thenReturn(new PlacePage(List.of(), 0, true));

		search(member(), Map.of("keyword", "없는코트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items").isEmpty())
				.andExpect(jsonPath("$.result.totalElements").value(0));
	}

	@Test
	void validatesCoordinatesRadiusAndKeywordBeforeCallingKakao() throws Exception {
		String member = member();
		for (var invalid : List.of(Map.of("keyword", "장충", "latitude", "127.0", "longitude", "37.5"),
				Map.of("keyword", "장충", "latitude", "37.5", "longitude", "181"),
				Map.of("keyword", "장충", "latitude", "37.5", "longitude", "127.0", "radius", "20001"),
				Map.of("keyword", "장충", "latitude", "37.5", "longitude", "127.0", "radius", "0"),
				Map.of("keyword", "장충", "size", "16"),
				Map.of("keyword", "장충", "page", "45"))) {
			search(member, invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
		}
		for (var withoutLocation : List.of(Map.of("keyword", "장충", "latitude", "37.5"),
				Map.of("keyword", "장충", "radius", "1000"), Map.of("keyword", "장충", "sort", "DISTANCE"))) {
			search(member, withoutLocation).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COURT_SEARCH_LOCATION_REQUIRED"));
		}
		search(member, Map.of("keyword", "  ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COURT_SEARCH_KEYWORD_REQUIRED"));
		verify(kakao, never()).searchKeyword(any());
	}

	@Test
	void distinguishesKakaoFailuresFromEmptyResults() throws Exception {
		when(kakao.searchKeyword(any())).thenThrow(new KakaoLocalClient.UnavailableException("timeout", null));

		search(member(), Map.of("keyword", "장충"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("COURT_SEARCH_UNAVAILABLE"));
	}

	@Test
	void limitsKakaoCallsPerMember() throws Exception {
		when(kakao.searchKeyword(any())).thenReturn(new PlacePage(List.of(), 0, true));
		String member = member();
		for (int i = 0; i < 5; i++) {
			search(member, Map.of("keyword", "장충")).andExpect(status().isOk());
		}
		search(member, Map.of("keyword", "장충")).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("COURT_SEARCH_RATE_LIMITED"));
		search(member(), Map.of("keyword", "장충")).andExpect(status().isOk());
		verify(kakao, times(6)).searchKeyword(any());
	}

	@Test
	void requiresAuthenticationAndOnboarding() throws Exception {
		mvc.perform(get("/api/v1/courts/search").param("keyword", "장충")).andExpect(status().isUnauthorized());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		var onboarding = users.saveAndFlush(User.signUp("대기" + suffix, "court-" + suffix + "@example.test"));
		userIds.add(onboarding.getId());
		search(bearer(onboarding), Map.of("keyword", "장충")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COURT_SEARCH_ONBOARDING_REQUIRED"));
		register(bearer(onboarding), registration("장충테니스장")).andExpect(status().isForbidden());
		verify(kakao, never()).searchKeyword(any());
	}

	@Test
	void registersTheVerifiedKakaoPlaceOnceAndReusesIt() throws Exception {
		stubVerifiedPlace();
		String member = member();

		var created = register(member, registration("장충테니스장"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMMON201"))
				.andExpect(jsonPath("$.result.kakaoPlaceId").value("8152837"))
				.andExpect(jsonPath("$.result.name").value("장충테니스장"))
				.andExpect(jsonPath("$.result.address").value("서울 중구 동호로 241"))
				.andExpect(jsonPath("$.result.areaCode").value("11140"))
				.andExpect(jsonPath("$.result.areaName").value("중구"))
				.andReturn().getResponse().getContentAsString();
		long courtId = json.readTree(created).path("result").path("courtId").asLong();

		assertThat(lastSearch()).isEqualTo(new KeywordSearch("장충테니스장", JANGCHUNG.latitude(), JANGCHUNG.longitude(),
				100, true, 1, 15));
		assertThat(jdbc.queryForMap("SELECT has_parking, has_shower, parking_note, facility_notes FROM court WHERE id = ?",
				courtId)).containsEntry("has_parking", false).containsEntry("has_shower", false)
				.containsEntry("parking_note", null).containsEntry("facility_notes", null);

		register(member(), registration("다른 이름"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.courtId").value(courtId))
				.andExpect(jsonPath("$.result.areaCode").value("11140"));
		verify(kakao, times(1)).searchKeyword(any());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court WHERE kakao_place_id = '8152837'", Long.class))
				.isEqualTo(1L);
	}

	@Test
	void storesOnlyKakaoDataAndRejectsPlacesThatCannotBeVerified() throws Exception {
		stubVerifiedPlace();
		String member = member();

		register(member, Map.of("kakaoPlaceId", "1234", "name", "가짜 코트", "latitude", 37.5, "longitude", 127.0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COURT_PLACE_NOT_VERIFIED"));
		register(member, Map.of("kakaoPlaceId", "abc", "name", "장충", "latitude", 37.5, "longitude", 127.0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON400"));
		when(kakao.legalDistrictCode(any(), any())).thenReturn(Optional.of("4113510900"));
		register(member, registration("장충테니스장"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COURT_UNSUPPORTED_AREA"));
		when(kakao.legalDistrictCode(any(), any())).thenThrow(new KakaoLocalClient.UnavailableException("down", null));
		register(member, registration("장충테니스장"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("COURT_SEARCH_UNAVAILABLE"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court WHERE kakao_place_id IS NOT NULL", Long.class))
				.isZero();
	}

	@Test
	void concurrentRegistrationsCreateOneCourt() throws Exception {
		var verifying = new CountDownLatch(2);
		when(kakao.searchKeyword(any())).thenAnswer(call -> {
			verifying.countDown();
			verifying.await(10, TimeUnit.SECONDS);
			return new PlacePage(List.of(JANGCHUNG), 1, true);
		});
		when(kakao.legalDistrictCode(any(), any())).thenReturn(Optional.of("1114016200"));
		var members = List.of(member(), member());
		var executor = Executors.newFixedThreadPool(2);
		try {
			var calls = new ArrayList<Callable<Integer>>();
			for (String member : members) {
				calls.add(() -> register(member, registration("장충테니스장")).andReturn().getResponse().getStatus());
			}
			var statuses = new ArrayList<Integer>();
			for (var result : executor.invokeAll(calls, 30, TimeUnit.SECONDS)) {
				statuses.add(result.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(200, 201);
		} finally {
			executor.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court WHERE kakao_place_id = '8152837'", Long.class))
				.isEqualTo(1L);
	}

	private void stubVerifiedPlace() {
		var nearby = new Place("777", "장충테니스장 주차장", "서울 중구 동호로 240", null, new BigDecimal("37.558700"),
				new BigDecimal("127.004400"), 5, "교통,수송 > 주차장");
		when(kakao.searchKeyword(any())).thenReturn(new PlacePage(List.of(nearby, JANGCHUNG), 2, true));
		when(kakao.legalDistrictCode(any(), any())).thenReturn(Optional.of("1114016200"));
	}

	private Map<String, Object> registration(String name) {
		return Map.of("kakaoPlaceId", "8152837", "name", name, "latitude", 37.558651, "longitude", 127.004377);
	}

	private KeywordSearch lastSearch() {
		var captor = ArgumentCaptor.forClass(KeywordSearch.class);
		verify(kakao, atLeastOnce()).searchKeyword(captor.capture());
		return captor.getValue();
	}

	private ResultActions search(String bearer, Map<String, String> parameters) throws Exception {
		var request = get("/api/v1/courts/search").header("Authorization", bearer);
		parameters.forEach(request::param);
		return mvc.perform(request);
	}

	private ResultActions register(String bearer, Map<String, Object> body) throws Exception {
		return mvc.perform(post("/api/v1/courts").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private String member() {
		var user = users.save(EntityFixtures.user(entityManager, "코트" + UUID.randomUUID().toString().substring(0, 8),
				Gender.FEMALE));
		userIds.add(user.getId());
		return bearer(user);
	}

	private String bearer(User user) {
		return "Bearer " + sessions.login(user.getId(), LoginTarget.SERVICE).accessToken();
	}
}
