package com.rallit.domain.user.repository;

import com.rallit.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") Long id);

  @Query(value = "select u.* from users u where u.email_key = lower(:email)", nativeQuery = true)
  Optional<User> findByEmailKey(@Param("email") String email);

  @Query(value = """
      select exists (select 1 from users where lower(nickname) = lower(:nickname) and id <> :userId)
      """, nativeQuery = true)
  boolean isNicknameTakenByAnotherUser(@Param("nickname") String nickname, @Param("userId") Long userId);
}
