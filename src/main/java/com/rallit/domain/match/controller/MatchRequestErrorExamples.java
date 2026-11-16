package com.rallit.domain.match.controller;

import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.models.examples.Example;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
class MatchRequestErrorExamples {

	private static final String EXAMPLE_PREFIX = "#/components/examples/";
	private static final String INVALID_DATE = "matchRequestInvalidDate";
	private static final String INVALID_PREFERENCES = "matchRequestInvalidPreferences";
	private static final String NOT_FOUND = "matchRequestNotFound";
	private static final String LIMIT_REACHED = "matchRequestLimitReached";
	private static final String ONBOARDING_REQUIRED = "matchRequestOnboardingRequired";

	static final String INVALID_DATE_REF = EXAMPLE_PREFIX + INVALID_DATE;
	static final String INVALID_PREFERENCES_REF = EXAMPLE_PREFIX + INVALID_PREFERENCES;
	static final String NOT_FOUND_REF = EXAMPLE_PREFIX + NOT_FOUND;
	static final String LIMIT_REACHED_REF = EXAMPLE_PREFIX + LIMIT_REACHED;

	@Bean
	OpenApiCustomizer registerMatchRequestErrorExamples() {
		return api -> api.getComponents()
				.addExamples(INVALID_DATE, example(MatchErrorStatus.MATCH_REQUEST_INVALID_DATE))
				.addExamples(INVALID_PREFERENCES, example(MatchErrorStatus.MATCH_REQUEST_INVALID_PREFERENCES))
				.addExamples(NOT_FOUND, example(MatchErrorStatus.MATCH_REQUEST_NOT_FOUND))
				.addExamples(LIMIT_REACHED, example(MatchErrorStatus.MATCH_REQUEST_LIMIT_REACHED))
				.addExamples(ONBOARDING_REQUIRED, example(MatchErrorStatus.MATCH_REQUEST_ONBOARDING_REQUIRED));
	}

	@Bean
	OperationCustomizer matchRequestOnboardingResponse() {
		return (operation, handler) -> {
			if (handler.getBeanType() == MatchRequestController.class) {
				var forbidden = operation.getResponses().get("403");
				forbidden.setDescription(MatchErrorStatus.MATCH_REQUEST_ONBOARDING_REQUIRED.getMessage());
				forbidden.getContent().get(MediaType.APPLICATION_JSON_VALUE)
						.addExamples("온보딩 미완료", new Example().$ref(EXAMPLE_PREFIX + ONBOARDING_REQUIRED));
			}
			return operation;
		};
	}

	private static Example example(MatchErrorStatus status) {
		var reason = status.getReason();
		return new Example().value(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), null));
	}
}
