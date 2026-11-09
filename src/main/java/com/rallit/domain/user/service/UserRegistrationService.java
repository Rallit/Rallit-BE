package com.rallit.domain.user.service;

import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.repository.UserConstraintViolations;
import com.rallit.domain.user.repository.UserRepository;
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

  public User getOrCreateKakaoUser(String kakaoId) {
    if (kakaoId == null || kakaoId.isBlank())
      throw new IllegalArgumentException("Missing provider identity");
    var found = users.findByProviderAndProviderUserId("KAKAO", kakaoId);
    if (found.isPresent()) return found.get();
    for (int i = 0; i < 5; i++) {
      String nickname = generator.generate();
      try {
        return new TransactionTemplate(manager)
            .execute(s -> users.saveAndFlush(User.registerKakao(kakaoId, nickname)));
      } catch (DataIntegrityViolationException e) {
        var existing = users.findByProviderAndProviderUserId("KAKAO", kakaoId);
        if (existing.isPresent()) return existing.get();
        if (!UserConstraintViolations.isNicknameConflict(e)) throw e;
      }
    }
    throw new IllegalStateException("Unable to allocate nickname");
  }
}
