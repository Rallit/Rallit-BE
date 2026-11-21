package com.rallit.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	public static final String SERVICE_ZONE_ID = "Asia/Seoul";

	public static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
