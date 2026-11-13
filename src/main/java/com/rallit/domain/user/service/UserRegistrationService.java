package com.rallit.domain.user.service;

import com.rallit.domain.auth.dto.internal.SocialIdentity;
import com.rallit.domain.auth.exception.SocialLoginRejectedException;
import com.rallit.domain.auth.exception.SocialLoginRejectedException.Reason;
import com.rallit.domain.auth.model.OAuthProvider;
import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.SocialProvider;
import com.rallit.domain.user.repository.SocialAccountRepository;
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
  private final SocialAccountRepository accounts;
  private final PlatformTransactionManager manager;
  private final NicknameGenerator generator;

  public Optional<User> findUserBySocialIdentity(SocialIdentity identity) {
    return accounts.findUserByProviderAndProviderUserId(
        SocialProvider.valueOf(identity.provider().name()), identity.subject());
  }

  public User getOrCreateUser(SocialIdentity identity) {
    String email = identity.verifiedEmail();
    for (int attempt = 0; attempt < 5; attempt++) {
      var existing = findUserBySocialIdentity(identity);
      if (existing.isPresent()) return syncExistingUser(existing.get(), identity);
      if (email == null) throw new SocialLoginRejectedException(Reason.EMAIL_REQUIRED, null);

      var emailOwner = users.findByEmailKey(email);
      if (emailOwner.isPresent()) {
        // The same identity may have registered after the initial lookup.
        existing = findUserBySocialIdentity(identity);
        if (existing.isPresent()) return syncExistingUser(existing.get(), identity);
        throw accountExists(emailOwner.get());
      }

      try {
        return createUser(identity, email);
      } catch (DataIntegrityViolationException failure) {
        if (!UserConstraintViolations.isEmailConflict(failure)
            && !UserConstraintViolations.isNicknameConflict(failure)
            && findUserBySocialIdentity(identity).isEmpty()) throw failure;
      }
    }
    throw new IllegalStateException("Unable to register member after concurrent conflicts");
  }

  private User syncVerifiedEmail(User existing, String email) {
    if (existing.isWithdrawn() || email == null || Objects.equals(email, existing.getEmail()))
      return existing;
    if (users.findByEmailKey(email).filter(owner -> !owner.getId().equals(existing.getId())).isPresent())
      return existing;

    try {
      return new TransactionTemplate(manager)
          .execute(
              status -> {
                User user = users.findByIdForUpdate(existing.getId()).orElseThrow();
                if (user.isWithdrawn()) return user;
                user.updateVerifiedEmail(email);
                return users.saveAndFlush(user);
              });
    } catch (DataIntegrityViolationException failure) {
      if (!UserConstraintViolations.isEmailConflict(failure)) throw failure;
      // Another member claimed the address after the lookup.
      return users.findById(existing.getId()).orElseThrow();
    }
  }

  private User syncExistingUser(User existing, SocialIdentity identity) {
    User user = syncVerifiedEmail(existing, identity.verifiedEmail());
    if (!user.isWithdrawn() && identity.displayName() != null) {
      accounts.updateDisplayName(
          SocialProvider.valueOf(identity.provider().name()), identity.subject(), identity.displayName());
    }
    return user;
  }

  private User createUser(SocialIdentity identity, String email) {
    return new TransactionTemplate(manager)
        .execute(
            status -> {
              User user = users.save(User.signUp(generator.generate(), email));
              SocialProvider provider = SocialProvider.valueOf(identity.provider().name());
              accounts.saveAndFlush(SocialAccount.of(user, provider, identity.subject(), identity.displayName()));
              return user;
            });
  }

  private SocialLoginRejectedException accountExists(User owner) {
    var account = accounts.findFirstByUserIdOrderByIdAsc(owner.getId()).orElseThrow();
    return new SocialLoginRejectedException(
        Reason.ACCOUNT_EXISTS, OAuthProvider.valueOf(account.getProvider().name()));
  }
}
