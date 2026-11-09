package com.rallit.domain.auth.service;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.auth.model.SessionTokens;
import com.rallit.domain.auth.model.SocialIdentity;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.domain.user.service.UserRegistrationService;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {
  private final UserRegistrationService registration;
  private final UserRepository users;
  private final TokenSessionService sessions;

  public SessionTokens login(SocialIdentity identity, LoginTarget target) {
    User user =
        switch (target) {
          case SERVICE -> registration.getOrCreateUser(identity);
          case ADMIN ->
              users
                  .findByProviderAndProviderUserId(identity.provider().name(), identity.subject())
                  .orElseThrow(() -> new GeneralException(ErrorStatus._FORBIDDEN));
        };
    return sessions.login(user.getId(), target);
  }
}
