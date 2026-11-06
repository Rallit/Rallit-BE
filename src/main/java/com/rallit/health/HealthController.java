package com.rallit.health;

import lombok.Builder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public HealthResponse health() {
		return HealthResponse.builder().status("ok").build();
	}

	@Builder
	public record HealthResponse(String status) {
	}
}
