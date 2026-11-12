package com.rallit.global.security.oauth2.google;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.model.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class GoogleIdentityMapper {
  private GoogleIdentityMapper() {}

  public static SocialIdentity map(OAuth2User principal) {
    if (!(principal instanceof OidcUser oidc))
      throw new IllegalArgumentException("Missing verified OIDC principal");
    var token = oidc.getIdToken();
    return new SocialIdentity(
        OAuthProvider.GOOGLE,
        token.getSubject(),
        token.getEmail(),
        Boolean.TRUE.equals(token.getEmailVerified()));
  }
}
