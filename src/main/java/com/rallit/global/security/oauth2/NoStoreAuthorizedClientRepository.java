package com.rallit.global.security.oauth2;

import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

public class NoStoreAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String id, Authentication a, HttpServletRequest r) {
    return null;
  }

  @Override
  public void saveAuthorizedClient(
      OAuth2AuthorizedClient c, Authentication a, HttpServletRequest r, HttpServletResponse s) {}

  @Override
  public void removeAuthorizedClient(
      String id, Authentication a, HttpServletRequest r, HttpServletResponse s) {}
}
