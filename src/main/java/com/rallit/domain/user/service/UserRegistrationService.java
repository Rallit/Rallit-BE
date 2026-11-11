package com.rallit.domain.user.service;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.exception.SocialLoginRejectedException.Reason;
import com.rallit.domain.auth.model.OAuthProvider;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.repository.UserConstraintViolations;
import com.rallit.domain.user.repository.UserRepository;
import java.util.Objects;
import java.util.Optional;
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
      var emailOwner = findMatchingEmailOwner(identity, email);
      if (emailOwner.isPresent() && email.equals(emailOwner.get().getEmail()))
        return emailOwner.get();
      var registeredUser = emailOwner.or(() -> existing);
      try {
        return saveUserWithVerifiedEmail(identity, email, registeredUser);
      } catch (DataIntegrityViolationException failure) {
        if (UserConstraintViolations.isEmailConflict(failure)) {
          if (findMatchingEmailOwner(identity, email).isPresent()
              || users
                  .findByProviderAndProviderUserId(identity.provider().name(), identity.subject())
                  .isPresent()) continue;
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

  private User saveUserWithVerifiedEmail(
      SocialIdentity identity, String email, Optional<User> registeredUser) {
    return new TransactionTemplate(manager)
        .execute(
            status -> {
              User user =
                  registeredUser
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
  }

  private Optional<User> findMatchingEmailOwner(SocialIdentity identity, String email) {
    return users
        .findByEmailKey(email)
        .map(
            owner -> {
              if (identity.provider().name().equals(owner.getProvider())
                  && identity.subject().equals(owner.getProviderUserId())) return owner;
              throw new SocialLoginRejectedException(
                  Reason.ACCOUNT_EXISTS, OAuthProvider.valueOf(owner.getProvider()));
            });
  }
}
