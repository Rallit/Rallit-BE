package com.rallit.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.service.TokenSessionService;
import com.rallit.domain.court.entity.Court;
import com.rallit.domain.court.repository.CourtRepository;
import com.rallit.domain.image.repository.ImageUploadRepository;
import com.rallit.domain.image.service.ImageNormalizer;
import com.rallit.domain.match.repository.MatchRepository;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.Gender;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.config.S3PresignedProperties;
import com.rallit.global.config.TimeConfig;
import com.rallit.global.storage.PresignedImageStorage;
import com.rallit.support.EntityFixtures;
import com.rallit.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@AutoConfigureMockMvc
class MatchCreationIntegrationTest extends IntegrationTestSupport {

	private static final String UPLOAD_URL = "https://image-test.s3.example.test/";
	private static final String ADMIN_ORIGIN = "https://admin.rallit.co.kr";
	private static final String EVIDENCE_PREFIX = "reservation-evidence/";
	private static final int MAX_BYTES = ImageNormalizer.MAX_BYTES;

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private TokenSessionService sessions;
	@Autowired private UserRepository users;
	@Autowired private CourtRepository courts;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private Clock clock;
	@Autowired private S3PresignedProperties presignedProperties;
	@MockitoBean private S3Client client;
	@MockitoBean private PresignedImageStorage presigned;
	@MockitoSpyBean private MatchRepository matchRepository;
	@MockitoSpyBean private ImageUploadRepository uploadRepository;

	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> courtIds = new ArrayList<>();
	private LocalDate date;

	@BeforeEach
	void setUp() throws IOException {
		date = LocalDate.ofInstant(clock.instant(), TimeConfig.SERVICE_ZONE).plusDays(3);
		when(presigned.signUpload(anyString(), any(MediaType.class), eq((long) MAX_BYTES)))
				.thenAnswer(call -> new PresignedImageStorage.SignedUpload(UPLOAD_URL, Map.of("key", call.getArgument(0)),
						clock.instant().plus(presignedProperties.uploadLifetime())));
	}

	@AfterEach
	void tearDown() {
		for (Long courtId : courtIds) {
			jdbc.update("""
					DELETE FROM match_application WHERE match_id IN (
					    SELECT m.id FROM match m JOIN court_slot s ON s.id = m.court_slot_id WHERE s.court_id = ?)
					""", courtId);
			jdbc.update("DELETE FROM match WHERE court_slot_id IN (SELECT id FROM court_slot WHERE court_id = ?)", courtId);
			jdbc.update("DELETE FROM court_slot WHERE court_id = ?", courtId);
			jdbc.update("DELETE FROM court WHERE id = ?", courtId);
		}
		for (Long userId : userIds) {
			jdbc.update("DELETE FROM image_upload WHERE owner_id = ?", userId);
			jdbc.update("DELETE FROM users WHERE id = ?", userId);
		}
	}

	@Test
	void opensARecruitingMatchWithoutEvidenceThatOthersCanFindAndApplyTo() throws Exception {
		Member host = member(Gender.MALE);
		long courtId = court();

		var request = body(courtId, "14:00", "16:00", null, recruitment -> { });
		reservation(request).put("courtNumber", " 3 ");
		var created = create(host, request)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMMON201"))
				.andExpect(jsonPath("$.result.matchStatus").value("RECRUITING"))
				.andExpect(jsonPath("$.result.courtId").value(courtId))
				.andExpect(jsonPath("$.result.startsAt").value(date + "T05:00:00Z"))
				.andExpect(jsonPath("$.result.endsAt").value(date + "T07:00:00Z"))
				.andExpect(jsonPath("$.result.recruitmentDeadline").value(date + "T04:00:00Z"))
				.andExpect(jsonPath("$.result.evidenceReviewStatus").value("NOT_SUBMITTED"))
				.andExpect(jsonPath("$.result.evidenceVerified").value(false))
				.andExpect(jsonPath("$.result.createdAt").isString())
				.andReturn().getResponse();
		long matchId = json.readTree(created.getContentAsString()).path("result").path("matchId").asLong();
		assertThat(created.getHeader("Location")).isEqualTo("/api/v1/matches/" + matchId);

		assertThat(jdbc.queryForMap("""
				SELECT s.court_number, s.evidence_file_key, s.evidence_review_status, s.registrant_user_id,
				    m.status, m.host_user_id, m.participation_fee, m.male_recruit_count, m.female_recruit_count
				FROM match m JOIN court_slot s ON s.id = m.court_slot_id WHERE m.id = ?
				""", matchId)).containsEntry("court_number", "3")
				.containsEntry("evidence_file_key", null).containsEntry("evidence_review_status", "NOT_SUBMITTED")
				.containsEntry("registrant_user_id", host.id()).containsEntry("status", "RECRUITING")
				.containsEntry("host_user_id", host.id()).containsEntry("participation_fee", 10000)
				.containsEntry("male_recruit_count", 1).containsEntry("female_recruit_count", 2);
		assertThat(jdbc.queryForList("SELECT age_group FROM match_preferred_age_group WHERE match_id = ?", String.class,
				matchId)).containsExactlyInAnyOrder("TWENTIES", "THIRTIES");

		Member applicant = member(Gender.FEMALE);
		mvc.perform(get("/api/v1/matches").header("Authorization", applicant.bearer()).param("date", date.toString())
						.param("applicableOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[?(@.matchId == " + matchId + ")].evidenceVerified").value(false));
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", applicant.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.canApply").value(true))
				.andExpect(jsonPath("$.result.capacity").value(4))
				.andExpect(jsonPath("$.result.evidenceVerified").value(false));
		apply(applicant, matchId).andExpect(status().isCreated());
		hostApplications(host, matchId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.match.evidence.submitted").value(false))
				.andExpect(jsonPath("$.result.match.evidence.reviewStatus").value("NOT_SUBMITTED"))
				.andExpect(jsonPath("$.result.match.evidence.verified").value(false))
				.andExpect(jsonPath("$.result.pendingApplications.length()").value(1));
	}

	@Test
	void opensAMatchWithEvidenceStoredPrivatelyAndPendingReview() throws Exception {
		Member host = member(Gender.FEMALE);
		long courtId = court();
		var uploadImage = png(600, 1800);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);

		long matchId = matchId(create(host, body(courtId, "22:00", "24:00", upload, recruitment -> {
			recruitment.put("matchTypes", List.of("WOMENS_DOUBLES"));
			recruitment.put("sameGenderOnly", true);
			recruitment.put("maleRecruitCount", 0);
			recruitment.put("femaleRecruitCount", 3);
		}))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.endsAt").value(date + "T15:00:00Z"))
				.andExpect(jsonPath("$.result.evidenceReviewStatus").value("PENDING"))
				.andExpect(jsonPath("$.result.evidenceVerified").value(false)));

		String key = evidenceKey(matchId);
		assertThat(key).matches("[0-9a-f-]{36}\\.jpg");
		var put = ArgumentCaptor.forClass(PutObjectRequest.class);
		var stored = ArgumentCaptor.forClass(RequestBody.class);
		verify(client).putObject(put.capture(), stored.capture());
		assertThat(put.getValue().key()).isEqualTo(EVIDENCE_PREFIX + key);
		try (var stream = stored.getValue().contentStreamProvider().newStream()) {
			var image = ImageIO.read(stream);
			assertThat(image.getWidth()).isEqualTo(600);
			assertThat(image.getHeight()).isEqualTo(1800);
		}
		verify(presigned).deleteUpload(upload.stagingKey());
		verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
		assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM image_upload WHERE id = ?", Boolean.class,
				upload.id())).isTrue();

		Member viewer = member(Gender.FEMALE);
		var detail = mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", viewer.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.evidenceVerified").value(false))
				.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain(key, EVIDENCE_PREFIX);
		hostApplications(host, matchId)
				.andExpect(jsonPath("$.result.match.evidence.submitted").value(true))
				.andExpect(jsonPath("$.result.match.evidence.reviewStatus").value("PENDING"))
				.andExpect(jsonPath("$.result.match.evidence.reviewedAt", nullValue()));
	}

	@Test
	void rejectsInvalidReservationsAndRecruitmentConditionsWithoutSavingAnything() throws Exception {
		Member host = member(Gender.MALE);
		long courtId = court();
		Map<String, Consumer<Map<String, Object>>> commonErrors = Map.of(
				"빈 코트 번호", reservation -> reservation.put("courtNumber", "  "),
				"긴 코트 번호", reservation -> reservation.put("courtNumber", "1".repeat(21)),
				"잘못된 시각", reservation -> reservation.put("startTime", "24:00"),
				"증빙 여러 장", reservation -> reservation.put("evidenceUploadId",
						List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())));
		for (var invalid : commonErrors.entrySet()) {
			var body = body(courtId, "14:00", "16:00", null, recruitment -> { });
			invalid.getValue().accept(reservation(body));
			create(host, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
		}
		Map<String, Consumer<Map<String, Object>>> recruitmentErrors = Map.of(
				"음수 참가비", recruitment -> recruitment.put("participationFee", -1),
				"음수 인원", recruitment -> recruitment.put("maleRecruitCount", -1),
				"경기 방식 없음", recruitment -> recruitment.put("matchTypes", List.of()));
		for (var invalid : recruitmentErrors.entrySet()) {
			create(host, body(courtId, "14:00", "16:00", null, invalid.getValue()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400"));
		}

		create(host, body(courtId, "16:00", "14:00", null, recruitment -> { }))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_INVALID_SCHEDULE"));
		var past = body(courtId, "14:00", "16:00", null, recruitment -> { });
		reservation(past).put("date", LocalDate.ofInstant(clock.instant(), TimeConfig.SERVICE_ZONE).minusDays(1).toString());
		create(host, past).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_START_TOO_SOON"));
		var tooFar = body(courtId, "14:00", "16:00", null, recruitment -> { });
		reservation(tooFar).put("date", LocalDate.ofInstant(clock.instant(), TimeConfig.SERVICE_ZONE).plusDays(15).toString());
		create(host, tooFar).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_RESERVATION_DATE_OUT_OF_RANGE"));
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> {
			recruitment.put("maleRecruitCount", 3);
			recruitment.put("femaleRecruitCount", 3);
		})).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_INVALID_RECRUIT_COUNT"));
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> recruitment.put("sameGenderOnly", true)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_SAME_GENDER_CONFLICT"));
		create(host, body(courtId, "14:00", "16:00", null,
				recruitment -> recruitment.put("matchTypes", List.of("WOMENS_DOUBLES"))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_TYPE_GENDER_CONFLICT"));
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> recruitment.put("maxNtrp", 4.5)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MATCH_INVALID_RECRUITMENT_NTRP"));
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> {
			recruitment.put("minNtrp", 3.5);
			recruitment.put("maxNtrp", 3.0);
		})).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATCH_INVALID_RECRUITMENT_NTRP"));
		create(host, body(Long.MAX_VALUE, "14:00", "16:00", null, recruitment -> { }))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COURT_NOT_FOUND"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId)).isZero();
	}

	@Test
	void blocksOverlappingReservationsAndKeepsTheEvidenceTicketForARetry() throws Exception {
		Member host = member(Gender.MALE);
		long courtId = court();
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> { })).andExpect(status().isCreated());
		var uploadImage = png(40, 40);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);

		create(member(Gender.MALE), body(courtId, "15:00", "17:00", null, recruitment -> { }))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COURT_SLOT_OVERLAPPED"));
		create(host, body(courtId, "15:00", "17:00", upload, recruitment -> { }))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COURT_SLOT_OVERLAPPED"));
		var discarded = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(client).putObject(discarded.capture(), any(RequestBody.class));
		verify(client).deleteObject(DeleteObjectRequest.builder().bucket("image-test").key(discarded.getValue().key())
				.build());
		verify(presigned, never()).deleteUpload(upload.stagingKey());
		assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM image_upload WHERE id = ?", Boolean.class,
				upload.id())).isTrue();

		var otherNumber = body(courtId, "15:00", "17:00", null, recruitment -> { });
		reservation(otherNumber).put("courtNumber", "4");
		create(host, otherNumber).andExpect(status().isCreated());
		long retried = matchId(create(host, body(courtId, "16:00", "18:00", upload, recruitment -> { }))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.evidenceReviewStatus").value("PENDING")));
		assertThat(EVIDENCE_PREFIX + evidenceKey(retried)).isNotEqualTo(discarded.getValue().key());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId))
				.isEqualTo(3L);
	}

	@Test
	void acceptsOnlyOneOfConcurrentRequestsForTheSameReservation() throws Exception {
		long courtId = court();
		var hosts = List.of(member(Gender.MALE), member(Gender.MALE));
		var executor = Executors.newFixedThreadPool(2);
		try {
			var calls = new ArrayList<Callable<Integer>>();
			for (Member host : hosts) {
				calls.add(() -> create(host, body(courtId, "14:00", "16:00", null, recruitment -> { }))
						.andReturn().getResponse().getStatus());
			}
			var statuses = new ArrayList<Integer>();
			for (var result : executor.invokeAll(calls, 30, TimeUnit.SECONDS)) {
				statuses.add(result.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		} finally {
			executor.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId))
				.isEqualTo(1L);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM match m JOIN court_slot s ON s.id = m.court_slot_id WHERE s.court_id = ?
				""", Long.class, courtId)).isEqualTo(1L);
	}

	@Test
	void rollsBackTheReservationAndDeletesTheEvidenceWhenTheMatchCannotBeSaved() throws Exception {
		Member host = member(Gender.MALE);
		long courtId = court();
		var uploadImage = png(40, 40);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);
		doThrow(new IllegalStateException("match insert failed")).when(matchRepository).saveAndFlush(any());

		create(host, body(courtId, "14:00", "16:00", upload, recruitment -> { }))
				.andExpect(status().isInternalServerError());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId)).isZero();
		var stored = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(client).putObject(stored.capture(), any(RequestBody.class));
		verify(client).deleteObject(DeleteObjectRequest.builder().bucket("image-test").key(stored.getValue().key())
				.build());
		assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM image_upload WHERE id = ?", Boolean.class,
				upload.id())).isTrue();
	}

	@Test
	void acceptsOnlyTheHostsOwnUnusedEvidenceUpload() throws Exception {
		Member host = member(Gender.MALE);
		Member other = member(Gender.MALE);
		long courtId = court();
		var othersUploadImage = png(40, 40);
		var othersUpload = issueEvidence(other, othersUploadImage);
		stage(othersUpload, othersUploadImage);
		create(host, body(courtId, "08:00", "09:00", othersUpload, recruitment -> { }))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_NOT_FOUND"));

		var profileImage = png(40, 40);
		var profileUpload = issue("/api/v1/users/me/profile-image/uploads", host, profileImage);
		stage(profileUpload, profileImage);
		create(host, body(courtId, "08:00", "09:00", profileUpload, recruitment -> { }))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_NOT_FOUND"));

		var notUploaded = issueEvidence(host, png(40, 40));
		create(host, body(courtId, "08:00", "09:00", notUploaded, recruitment -> { }))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_NOT_READY"));

		var expiredImage = png(40, 40);
		var expired = issueEvidence(host, expiredImage);
		stage(expired, expiredImage);
		jdbc.update("UPDATE image_upload SET expires_at = current_timestamp - interval '1 second' WHERE id = ?",
				expired.id());
		create(host, body(courtId, "08:00", "09:00", expired, recruitment -> { }))
				.andExpect(status().isGone()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_EXPIRED"));
		verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

		var usedImage = png(40, 40);
		var used = issueEvidence(host, usedImage);
		stage(used, usedImage);
		create(host, body(courtId, "08:00", "09:00", used, recruitment -> { })).andExpect(status().isCreated());
		create(host, body(courtId, "10:00", "11:00", used, recruitment -> { }))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_ALREADY_USED"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId))
				.isEqualTo(1L);
	}

	@Test
	void reportsAnEvidenceUploadConsumedByAConcurrentRequestAsAlreadyUsed() throws Exception {
		Member host = member(Gender.MALE);
		long courtId = court();
		var uploadImage = png(40, 40);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);
		var unusedTicket = uploadRepository.findById(upload.id()).orElseThrow();
		create(host, body(courtId, "08:00", "09:00", upload, recruitment -> { })).andExpect(status().isCreated());
		var usedTicket = uploadRepository.findById(upload.id()).orElseThrow();
		when(presigned.readUpload(upload.stagingKey(), MAX_BYTES)).thenReturn(Optional.empty());
		doReturn(Optional.of(unusedTicket), Optional.of(usedTicket)).when(uploadRepository).findById(upload.id());

		create(host, body(courtId, "10:00", "11:00", upload, recruitment -> { }))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_ALREADY_USED"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId))
				.isEqualTo(1L);
	}

	@Test
	void requiresAnAuthenticatedOnboardedHost() throws Exception {
		long courtId = court();
		mvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(body(courtId, "14:00", "16:00", null, recruitment -> { }))))
				.andExpect(status().isUnauthorized());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		var onboarding = users.saveAndFlush(User.signUp("대기" + suffix, "host-" + suffix + "@example.test"));
		userIds.add(onboarding.getId());
		var waiting = new Member(onboarding.getId(), bearer(onboarding.getId(), LoginTarget.SERVICE));

		create(waiting, body(courtId, "14:00", "16:00", null, recruitment -> { }))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MATCH_ONBOARDING_REQUIRED"))
				.andExpect(jsonPath("$.message").value("온보딩을 완료해야 매치를 개설할 수 있습니다."));
		initiate("/api/v1/host/reservation-evidence/uploads", waiting, 1000).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM court_slot WHERE court_id = ?", Long.class, courtId)).isZero();
	}

	@Test
	void approvingEvidenceAddsTheBadgeWithoutChangingRecruitment() throws Exception {
		Member host = member(Gender.MALE);
		String admin = admin();
		long courtId = court();
		var uploadImage = png(40, 40);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);
		long matchId = matchId(create(host, body(courtId, "14:00", "16:00", upload, recruitment -> { })));
		long slotId = slotId(matchId);
		String key = evidenceKey(matchId);

		admin(get("/api/v1/admin/court-slots").param("size", "50"), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[?(@.courtSlotId == " + slotId + ")].registrant.userId")
						.value((int) host.id()))
				.andExpect(jsonPath("$.result.items[?(@.courtSlotId == " + slotId + ")].evidence.reviewStatus")
						.value("PENDING"));
		var signed = new PresignedImageStorage.SignedRead(UPLOAD_URL + EVIDENCE_PREFIX + key + "?X-Amz-Signature=test",
				clock.instant().plus(presignedProperties.downloadLifetime()));
		when(presigned.signRead(EVIDENCE_PREFIX, key)).thenReturn(signed);
		admin(get("/api/v1/admin/court-slots/{id}/evidence/url", slotId), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.url").value(signed.url()));

		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.courtSlotId").value(slotId))
				.andExpect(jsonPath("$.result.evidence.reviewStatus").value("APPROVED"))
				.andExpect(jsonPath("$.result.evidence.verified").value(true))
				.andExpect(jsonPath("$.result.evidence.reviewedAt").isString());
		assertThat(matchStatus(matchId)).isEqualTo("RECRUITING");
		Member viewer = member(Gender.FEMALE);
		mvc.perform(get("/api/v1/matches/{matchId}", matchId).header("Authorization", viewer.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.matchStatus").value("RECRUITING"))
				.andExpect(jsonPath("$.result.evidenceVerified").value(true));
		hostApplications(host, matchId)
				.andExpect(jsonPath("$.result.match.status").value("RECRUITING"))
				.andExpect(jsonPath("$.result.match.evidence.verified").value(true))
				.andExpect(jsonPath("$.result.match.evidence.reviewedAt").isString());

		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId), admin)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVIDENCE_REVIEW_NOT_PENDING"));
		rejectEvidence(admin, slotId, "사유").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVIDENCE_REVIEW_NOT_PENDING"));
	}

	@Test
	void rejectingEvidenceCancelsTheMatchAndReleasesTheReservedTime() throws Exception {
		Member host = member(Gender.MALE);
		String admin = admin();
		long courtId = court();
		var uploadImage = png(40, 40);
		var upload = issueEvidence(host, uploadImage);
		stage(upload, uploadImage);
		long matchId = matchId(create(host, body(courtId, "14:00", "16:00", upload, recruitment -> { })));
		long slotId = slotId(matchId);

		rejectEvidence(admin, slotId, " ").andExpect(status().isBadRequest());
		rejectEvidence(admin, slotId, " 예약자명이 보이지 않습니다. ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.evidence.reviewStatus").value("REJECTED"))
				.andExpect(jsonPath("$.result.evidence.verified").value(false))
				.andExpect(jsonPath("$.result.evidence.rejectionReason").value("예약자명이 보이지 않습니다."));

		assertThat(jdbc.queryForMap("""
				SELECT status, cancel_reason, canceled_at IS NOT NULL AS canceled FROM match WHERE id = ?
				""", matchId)).containsEntry("status", "CANCELED").containsEntry("cancel_reason", "예약자명이 보이지 않습니다.")
				.containsEntry("canceled", true);
		Member applicant = member(Gender.FEMALE);
		mvc.perform(get("/api/v1/matches").header("Authorization", applicant.bearer()).param("date", date.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.items[?(@.matchId == " + matchId + ")]").isEmpty());
		apply(applicant, matchId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_RECRUITING"));
		hostApplications(host, matchId)
				.andExpect(jsonPath("$.result.match.status").value("CANCELED"))
				.andExpect(jsonPath("$.result.match.evidence.reviewStatus").value("REJECTED"))
				.andExpect(jsonPath("$.result.match.evidence.rejectionReason").value("예약자명이 보이지 않습니다."));
		create(host, body(courtId, "14:00", "16:00", null, recruitment -> { })).andExpect(status().isCreated());
	}

	@Test
	void restrictsEvidenceReviewToAdminsAndSubmittedEvidence() throws Exception {
		Member host = member(Gender.MALE);
		long matchId = matchId(create(host, body(court(), "14:00", "16:00", null, recruitment -> { })));
		long slotId = slotId(matchId);
		String admin = admin();

		mvc.perform(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId)).andExpect(status().isForbidden());
		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId), host.bearer())
				.andExpect(status().isUnauthorized());
		admin(get("/api/v1/admin/court-slots"), host.bearer()).andExpect(status().isUnauthorized());
		Member demoted = member(Gender.FEMALE);
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", demoted.id());
		String demotedAdmin = bearer(demoted.id(), LoginTarget.ADMIN);
		jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", demoted.id());
		admin(get("/api/v1/admin/court-slots"), demotedAdmin).andExpect(status().isForbidden());
		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId), demotedAdmin)
				.andExpect(status().isForbidden());

		admin(get("/api/v1/admin/court-slots/{id}/evidence/url", slotId), admin)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVIDENCE_NOT_SUBMITTED"));
		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", slotId), admin)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVIDENCE_REVIEW_NOT_PENDING"));
		admin(post("/api/v1/admin/court-slots/{id}/evidence/approve", Long.MAX_VALUE), admin)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COURT_SLOT_NOT_FOUND"));
		assertThat(jdbc.queryForObject("SELECT evidence_review_status FROM court_slot WHERE id = ?", String.class,
				slotId)).isEqualTo("NOT_SUBMITTED");
	}

	private Map<String, Object> body(long courtId, String start, String end, Upload evidence,
			Consumer<Map<String, Object>> recruitmentChanges) {
		var reservation = new LinkedHashMap<String, Object>();
		reservation.put("courtId", courtId);
		reservation.put("courtNumber", "3");
		reservation.put("date", date.toString());
		reservation.put("startTime", start);
		reservation.put("endTime", end);
		reservation.put("evidenceUploadId", evidence == null ? null : evidence.id().toString());
		var recruitment = new LinkedHashMap<String, Object>();
		recruitment.put("matchTypes", List.of("MIXED_DOUBLES"));
		recruitment.put("sameGenderOnly", false);
		recruitment.put("maleRecruitCount", 1);
		recruitment.put("femaleRecruitCount", 2);
		recruitment.put("minNtrp", 2.5);
		recruitment.put("maxNtrp", 4.0);
		recruitment.put("preferredAgeGroups", List.of("TWENTIES", "THIRTIES"));
		recruitment.put("participationFee", 10000);
		recruitmentChanges.accept(recruitment);
		var body = new LinkedHashMap<String, Object>();
		body.put("reservation", reservation);
		body.put("recruitment", recruitment);
		return body;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> reservation(Map<String, Object> body) {
		return (Map<String, Object>) body.get("reservation");
	}

	private ResultActions create(Member host, Map<String, Object> body) throws Exception {
		return mvc.perform(post("/api/v1/matches").header("Authorization", host.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private long matchId(ResultActions created) throws Exception {
		var response = created.andExpect(status().isCreated()).andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		return json.readTree(response).path("result").path("matchId").asLong();
	}

	private ResultActions apply(Member applicant, long matchId) throws Exception {
		return mvc.perform(post("/api/v1/matches/{matchId}/applications", matchId)
				.header("Authorization", applicant.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"refundPolicyAgreed\":true}"));
	}

	private ResultActions hostApplications(Member host, long matchId) throws Exception {
		return mvc.perform(get("/api/v1/host/matches/{matchId}/applications", matchId)
				.header("Authorization", host.bearer()));
	}

	private ResultActions rejectEvidence(String admin, long slotId, String reason) throws Exception {
		return admin(post("/api/v1/admin/court-slots/{id}/evidence/reject", slotId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("reason", reason))), admin);
	}

	private ResultActions admin(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		return mvc.perform(request.header("Origin", ADMIN_ORIGIN).header("Authorization", bearer));
	}

	private Upload issueEvidence(Member host, byte[] contents) throws Exception {
		return issue("/api/v1/host/reservation-evidence/uploads", host, contents);
	}

	private Upload issue(String path, Member user, byte[] contents) throws Exception {
		var response = initiate(path, user, contents.length)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result.url").value(UPLOAD_URL))
				.andReturn().getResponse().getContentAsString();
		return new Upload(UUID.fromString(json.readTree(response).path("result").path("uploadId").asText()));
	}

	private ResultActions initiate(String path, Member user, long size) throws Exception {
		return mvc.perform(post(path).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("fileName", "reservation.png", "contentType", "image/png",
						"size", size))));
	}

	private void stage(Upload upload, byte[] contents) throws IOException {
		when(presigned.readUpload(upload.stagingKey(), MAX_BYTES)).thenReturn(Optional.of(
				new PresignedImageStorage.StagedImage(contents, "image/png")));
	}

	private String evidenceKey(long matchId) {
		return jdbc.queryForObject("""
				SELECT s.evidence_file_key FROM match m JOIN court_slot s ON s.id = m.court_slot_id WHERE m.id = ?
				""", String.class, matchId);
	}

	private long slotId(long matchId) {
		return jdbc.queryForObject("SELECT court_slot_id FROM match WHERE id = ?", Long.class, matchId);
	}

	private String matchStatus(long matchId) {
		return jdbc.queryForObject("SELECT status FROM match WHERE id = ?", String.class, matchId);
	}

	private long court() {
		Court court = courts.save(EntityFixtures.court());
		courtIds.add(court.getId());
		return court.getId();
	}

	private Member member(Gender gender) {
		var user = users.save(EntityFixtures.user(entityManager, "개설" + UUID.randomUUID().toString().substring(0, 8),
				gender));
		userIds.add(user.getId());
		return new Member(user.getId(), bearer(user.getId(), LoginTarget.SERVICE));
	}

	private String admin() {
		var user = users.save(EntityFixtures.user(entityManager, "운영" + UUID.randomUUID().toString().substring(0, 8),
				Gender.FEMALE));
		userIds.add(user.getId());
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.getId());
		return bearer(user.getId(), LoginTarget.ADMIN);
	}

	private String bearer(long userId, LoginTarget target) {
		return "Bearer " + sessions.login(userId, target).accessToken();
	}

	private static byte[] png(int width, int height) throws IOException {
		var out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private record Member(long id, String bearer) {
	}

	private record Upload(UUID id) {
		String stagingKey() {
			return "image-uploads/" + id;
		}
	}
}
