package com.rallit.global.security.authentication;

import com.rallit.domain.user.enums.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record UserDetails(Long userId, UserRole role, boolean onboardingCompleted)
    implements org.springframework.security.core.userdetails.UserDetails {
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getUsername() {
    return userId.toString();
  }

  @Override
  public String getPassword() {
    return null;
  }
}
