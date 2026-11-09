package com.rallit.domain.user.entity;

import com.rallit.domain.user.enums.*;
import com.rallit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_user_id", nullable = false)
  private String providerUserId;

  @Column(nullable = false, length = 12)
  private String nickname;

  @Column(length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  public static User register(String provider, String subject, String nickname, String email) {
    User user = new User();
    user.provider = provider;
    user.providerUserId = subject;
    user.email = email;
    user.nickname = nickname;
    user.role = UserRole.USER;
    user.status = UserStatus.PENDING;
    return user;
  }

  public void updateVerifiedEmail(String email) {
    this.email = java.util.Objects.requireNonNull(email);
  }

  public boolean isOnboardingCompleted() {
    return status == UserStatus.ACTIVE;
  }
}
