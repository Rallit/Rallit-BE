package com.rallit.domain.match.controller;

import com.rallit.domain.match.exception.MatchErrorStatus;
import com.rallit.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.models.examples.Example;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MatchRequestErrorExamples {

	private static final String EXAMPLE_PREFIX = "#/components/examples/";
	private static final String INVALID_DATE = "matchRequestInvalidDate";
	private static final String NOT_FOUND = "matchRequestNotFound";
	private static final String LIMIT_REACHED = "matchRequestLimitReached";

	static final String INVALID_DATE_REF = EXAMPLE_PREFIX + INVALID_DATE;
	static final String NOT_FOUND_REF = EXAMPLE_PREFIX + NOT_FOUND;
	static final String LIMIT_REACHED_REF = EXAMPLE_PREFIX + LIMIT_REACHED;

	@Bean
	OpenApiCustomizer registerMatchRequestErrorExamples() {
		return api -> api.getComponents()
				.addExamples(INVALID_DATE, example(MatchErrorStatus.MATCH_REQUEST_INVALID_DATE))
				.addExamples(NOT_FOUND, example(MatchErrorStatus.MATCH_REQUEST_NOT_FOUND))
				.addExamples(LIMIT_REACHED, example(MatchErrorStatus.MATCH_REQUEST_LIMIT_REACHED));
	}

	private static Example example(MatchErrorStatus status) {
		var reason = status.getReason();
		return new Example().value(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), null));
	}
}
