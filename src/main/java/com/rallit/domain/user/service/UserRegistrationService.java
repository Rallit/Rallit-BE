package com.rallit.domain.user.service;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.exception.SocialLoginRejectedException.Reason;
import com.rallit.domain.auth.model.OAuthProvider;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.repository.UserConstraintViolations;
import com.rallit.domain.user.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {
  private final UserRepository users;
  private final PlatformTransactionManager manager;
  private final NicknameGenerator generator;

  public User getOrCreateUser(SocialIdentity identity) {
    String email = identity.verifiedEmail();
    for (int attempt = 0; attempt < 5; attempt++) {
      var existing =
          users.findByProviderAndProviderUserId(identity.provider().name(), identity.subject());
      if (existing.isPresent()
          && (email == null || Objects.equals(email, existing.get().getEmail())))
        return existing.get();
      if (email == null) throw new SocialLoginRejectedException(Reason.EMAIL_REQUIRED, null);
      rejectEmailOwner(email, existing.map(User::getId).orElse(null));
      try {
        return new TransactionTemplate(manager)
            .execute(
                status -> {
                  User user =
                      existing
                          .map(found -> users.findByIdForUpdate(found.getId()).orElseThrow())
                          .orElseGet(
                              () ->
                                  User.register(
                                      identity.provider().name(),
                                      identity.subject(),
                                      generator.generate(),
                                      email));
                  user.updateVerifiedEmail(email);
                  return users.saveAndFlush(user);
                });
      } catch (DataIntegrityViolationException failure) {
        if (UserConstraintViolations.isEmailConflict(failure)) {
          var concurrent =
              users.findByProviderAndProviderUserId(identity.provider().name(), identity.subject());
          rejectEmailOwner(email, concurrent.map(User::getId).orElse(null));
          if (concurrent.isPresent()) continue;
          throw failure;
        }
        if (users
            .findByProviderAndProviderUserId(identity.provider().name(), identity.subject())
            .isPresent()) continue;
        if (!UserConstraintViolations.isNicknameConflict(failure)) throw failure;
      }
    }
    throw new IllegalStateException("Unable to register member after concurrent conflicts");
  }

  private void rejectEmailOwner(String email, Long currentUserId) {
    users
        .findByEmailIgnoreCase(email)
        .filter(owner -> !owner.getId().equals(currentUserId))
        .ifPresent(
            owner -> {
              throw new SocialLoginRejectedException(
                  Reason.ACCOUNT_EXISTS, OAuthProvider.valueOf(owner.getProvider()));
            });
  }
}
