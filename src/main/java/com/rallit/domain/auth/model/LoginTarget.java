package com.rallit.domain.auth.model;

public enum LoginTarget {
  SERVICE,
  ADMIN;

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
