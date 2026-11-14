package com.rallit.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rallit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "RALLIT_ENV_FILE=.env.test-disabled")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class LocalDocsIntegrationTest extends IntegrationTestSupport {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void swaggerDocumentsSocialLoginAndSeparatesAdminOperations() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("인증", "회원", "매치 조회", "매치 참가 신청", "운영자")))
        .andExpect(jsonPath("$.paths['/api/v1/auth/kakao'].get.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/kakao'].get.responses['302']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/kakao'].get.parameters[*].name",
            containsInAnyOrder("target", "callback")))
        .andExpect(jsonPath("$.paths['/api/v1/auth/google'].get.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/google'].get.responses['302']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/google'].get.parameters[*].name",
            containsInAnyOrder("target", "callback")))
        .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.tags[0]").value("인증"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.tags[0]").value("회원"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/auth/refresh'].post.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/auth/logout'].post.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/me'].get.tags[0]").value("운영자"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/me'].get.security[0].bearerAuth").exists());
  }

  @Test
  void swaggerDocumentsPhotoDeletionAndOnboardingExample() throws Exception {
    String deletion = "$.paths['/api/v1/users/me/profile-image'].delete.responses";
    String onboarding = "$.paths['/api/v1/users/me/onboarding'].post";
    String jsonBody = onboarding + ".requestBody.content['application/json']";
    String example = jsonBody + ".examples['가입 완료 예시'].value";
    String profile = "$.components.schemas.UserResponse.properties.profile";
    String cityCode = "$.paths['/api/v1/cities/{cityCode}/areas'].get.parameters[0].example";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(deletion + "['204']").exists())
        .andExpect(jsonPath(deletion + "['204'].content").doesNotExist())
        .andExpect(jsonPath(deletion + "['200']").doesNotExist())
        .andExpect(jsonPath(onboarding + ".responses['400']").exists())
        .andExpect(jsonPath(onboarding + ".responses['409']").exists())
        .andExpect(jsonPath(jsonBody + ".schema['$ref']").value("#/components/schemas/OnboardingRequest"))
        .andExpect(jsonPath(example + ".ntrp").value("4.0+"))
        .andExpect(jsonPath(example + ".activityAreaCodes[0]").value("11440"))
        .andExpect(jsonPath(example + ".sameGenderOnly").value(false))
        .andExpect(jsonPath(profile + ".oneOf[0]['$ref']").value("#/components/schemas/UserProfileResponse"))
        .andExpect(jsonPath(profile + ".oneOf[1].type").value("null"))
        .andExpect(jsonPath(cityCode).isString())
        .andExpect(jsonPath(cityCode).value("11"));
  }

  @Test
  void swaggerSeparatesMatchDiscoveryParametersAndResponses() throws Exception {
    String list = "$.paths['/api/v1/matches'].get";
    String detail = "$.paths['/api/v1/matches/{matchId}'].get";
    String listJson = list + ".responses['200'].content['application/json']";
    String detailJson = detail + ".responses['200'].content['application/json']";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(list + ".tags[0]").value("매치 조회"))
        .andExpect(jsonPath(detail + ".tags[0]").value("매치 조회"))
        .andExpect(jsonPath(list + ".security[0].bearerAuth").exists())
        .andExpect(jsonPath(detail + ".security[0].bearerAuth").exists())
        .andExpect(jsonPath(list + ".parameters[*].name",
            containsInAnyOrder("date", "startTimeFrom", "startTimeTo", "matchTypes", "applicableOnly", "page", "size")))
        .andExpect(jsonPath(list + ".parameters[?(@.name == 'date')].schema.format").value("date"))
        .andExpect(jsonPath(list + ".parameters[?(@.name == 'applicableOnly')].schema.default").value(false))
        .andExpect(jsonPath(list + ".parameters[?(@.name == 'size')].schema.maximum").value(50))
        .andExpect(jsonPath(detail + ".parameters[0].name").value("matchId"))
        .andExpect(jsonPath(detail + ".parameters[0].required").value(true))
        .andExpect(jsonPath(listJson + ".schema['$ref']").isString())
        .andExpect(jsonPath(detailJson + ".schema['$ref']").isString())
        .andExpect(jsonPath(listJson + ".examples['모집 매치 목록'].value.result.items[0].startsAt")
            .value("2026-11-15T14:00:00+09:00"))
        .andExpect(jsonPath(listJson + ".examples['검색 결과 없음'].value.result.totalElements").value(0))
        .andExpect(jsonPath(detailJson + ".examples['신청 가능'].value.result.canApply").value(true))
        .andExpect(jsonPath(detailJson + ".examples['신청 가능'].value.result.recruitmentDeadline")
            .value("2026-11-15T13:00:00+09:00"))
        .andExpect(jsonPath("$.components.schemas.MatchListItemResponse.properties.startsAt.format").value("date-time"))
        .andExpect(jsonPath("$.components.schemas.MatchDetailResponse.properties.applicationBlockReason.type[*]",
            containsInAnyOrder("string", "null")))
        .andExpect(jsonPath(list + ".responses['400'].content['application/json']"
            + ".examples['페이지 범위 초과'].value.code").value("MATCH_PAGE_OUT_OF_RANGE"))
        .andExpect(jsonPath(list + ".responses['401'].content['application/json']"
            + ".examples['인증 필요'].value.code").value("AUTH401"))
        .andExpect(jsonPath(detail + ".responses['403'].content['application/json']"
            + ".examples['온보딩 미완료'].value.code").value("MATCH_ONBOARDING_REQUIRED"))
        .andExpect(jsonPath(detail + ".responses['404'].content['application/json']"
            + ".examples['조회 불가'].value.code").value("MATCH_NOT_FOUND"))
        .andExpect(jsonPath(detail + ".responses['503'].content['application/json']"
            + ".examples['세션 저장소 장애'].value.code").value("COMMON503"));
  }

  @Test
  void swaggerDocumentsPaginatedMatchAndApplicationItems() throws Exception {
    var response = mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn().getResponse();
    JsonNode openApi = json.readTree(response.getContentAsString());

    JsonNode matchPage = paginationSchema(openApi, "/api/v1/matches");
    assertThat(matchPage.path("required")).anyMatch(field -> field.asText().equals("totalElements"));
    JsonNode matches = matchPage.path("properties");
    assertThat(matches.propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "hasNext", "totalElements");
    assertThat(matches.at("/items/items/$ref").asText()).isEqualTo("#/components/schemas/MatchListItemResponse");

    for (String path : new String[]{"/api/v1/users/me/applications", "/api/v1/users/me/applications/closed"}) {
      JsonNode applicationPage = paginationSchema(openApi, path);
      assertThat(applicationPage.path("required")).anyMatch(field -> field.asText().equals("totalElements"));
      JsonNode applications = applicationPage.path("properties");
      assertThat(applications.propertyNames())
          .containsExactlyInAnyOrder("items", "page", "size", "hasNext", "totalElements");
      assertThat(applications.at("/items/items/$ref").asText())
          .isEqualTo("#/components/schemas/MyMatchApplicationListItemResponse");
    }
  }

  private JsonNode paginationSchema(JsonNode openApi, String path) {
    JsonNode content = openApi.path("paths").path(path).at("/get/responses/200/content");
    JsonNode jsonContent = content.path("application/json");
    String envelopeRef = (jsonContent.isMissingNode() ? content.path("*/*") : jsonContent)
        .at("/schema/$ref").asText();
    assertThat(envelopeRef).as("%s response content: %s", path, content).startsWith("#/");
    String resultRef = openApi.at(envelopeRef.substring(1)).at("/properties/result/$ref").asText();
    return openApi.at(resultRef.substring(1));
  }
}
