package com.rallit.domain.user.repository;

import com.rallit.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

  Optional<User> findByEmailIgnoreCase(String email);

  Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
}
