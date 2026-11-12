package com.rallit.global.security.oauth2.kakao;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.model.OAuthProvider;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class KakaoIdentityMapper {
  private KakaoIdentityMapper() {}

  public static SocialIdentity map(OAuth2User principal) {
    Object id = principal.getAttribute("id");
    if (!(id instanceof Number) || ((Number) id).longValue() <= 0)
      throw new IllegalArgumentException("Missing Kakao identity");
    Map<?, ?> account =
        principal.getAttribute("kakao_account") instanceof Map<?, ?> value ? value : Map.of();
    String email = account.get("email") instanceof String value ? value : null;
    boolean verified =
        Boolean.TRUE.equals(account.get("is_email_valid"))
            && Boolean.TRUE.equals(account.get("is_email_verified"));
    return new SocialIdentity(OAuthProvider.KAKAO, id.toString(), email, verified);
  }
}
