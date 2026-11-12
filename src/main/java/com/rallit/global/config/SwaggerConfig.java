package com.rallit.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {
	private static final String AUTH_TAG = "인증";
	private static final String ADMIN_TAG = "운영자";

	@Bean
	public OpenAPI rallitApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Rallit API")
						.description("토큰 재발급과 로그아웃에는 Refresh 쿠키, JSON 요청과 "
								+ "`X-Requested-With: XMLHttpRequest` 헤더가 필요합니다.")
						.version("0.0.1"))
				.tags(List.of(
						new Tag().name(AUTH_TAG).description("소셜 로그인, 토큰 재발급, 로그아웃"),
						new Tag().name("회원").description("일반 서비스의 내 정보 조회"),
						new Tag().name(ADMIN_TAG).description("운영자 정보 조회와 운영자 세션 관리")))
				.components(new Components().addSecuritySchemes("bearerAuth",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

	@Bean
	OpenApiCustomizer socialLoginAndAdminDocs() {
		return openApi -> {
			openApi.path("/api/v1/auth/kakao", socialLoginStart("kakao", "카카오"));
			openApi.path("/api/v1/auth/google", socialLoginStart("google", "구글"));
			openApi.getPaths().forEach((path, item) -> {
				if (path.startsWith("/api/v1/admin/")) {
					item.readOperations().forEach(operation -> operation.setTags(List.of(ADMIN_TAG)));
				}
			});
		};
	}

	@Bean
	OpenApiCustomizer nullableUserProfileDocs() {
		return openApi -> {
			Schema<?> userResponse = openApi.getComponents().getSchemas().get("UserResponse");
			if (userResponse == null) return;
			var profile = userResponse.getProperties().get("profile");
			if (profile == null || profile.get$ref() == null) return;
			String reference = profile.get$ref();
			profile.set$ref(null);
			profile.addOneOfItem(new Schema<>().$ref(reference));
			profile.addOneOfItem(new Schema<>().types(Set.of("null")));
		};
	}

	private PathItem socialLoginStart(String registrationId, String providerName) {
		var target = new StringSchema();
		target.setEnum(List.of("service", "admin"));
		target.setDefault("service");

		return new PathItem().get(new Operation()
				.operationId(registrationId + "LoginStart")
				.tags(List.of(AUTH_TAG))
				.summary(providerName + " 로그인 시작")
				.description("브라우저에서 이 주소를 열면 " + providerName + " 인증 화면으로 이동합니다.")
				.addParametersItem(new QueryParameter().name("target")
						.description("일반 서비스 또는 운영자 로그인")
						.schema(target))
				.responses(new ApiResponses()
						.addApiResponse("302", new ApiResponse().description("소셜 인증 화면으로 이동"))
						.addApiResponse("400", new ApiResponse().description("지원하지 않는 로그인 목적"))));
	}
}
