package com.rallit.domain.auth.model;

import java.util.Optional;

public enum LoginTarget {
  SERVICE,
  ADMIN;

  public static Optional<LoginTarget> fromParameter(String value) {
    return switch (value == null ? "service" : value) {
      case "service" -> Optional.of(SERVICE);
      case "admin" -> Optional.of(ADMIN);
      default -> Optional.empty();
    };
  }

  public String audience() {
    return this == ADMIN ? "rallit-admin" : "rallit-service";
  }

  public String prefix() {
    return this == ADMIN ? "rallit_admin" : "rallit";
  }

  public String apiPath() {
    return this == ADMIN ? "/api/v1/admin" : "/api/v1";
  }
}
