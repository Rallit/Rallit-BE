package com.rallit.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {

	@Bean
	public OpenAPI rallitApi() {
		return new OpenAPI().info(new Info()
				.title("Rallit API")
				.description("Rallit 백엔드 API 명세서")
				.version("0.0.1"));
	}
}
