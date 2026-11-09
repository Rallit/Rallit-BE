package com.rallit.global.security.authentication;

import com.rallit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailsService
    implements org.springframework.security.core.userdetails.UserDetailsService {
  private final UserRepository users;

  /** JWT subject is the stable member ID, rather than an editable nickname. */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Long userId;
    try {
      userId = Long.valueOf(username);
    } catch (NumberFormatException invalid) {
      throw new UsernameNotFoundException("Invalid member identifier");
    }
    if (userId <= 0) throw new UsernameNotFoundException("Invalid member identifier");
    var user =
        users.findById(userId).orElseThrow(() -> new UsernameNotFoundException("Member not found"));
    return new UserDetails(user.getId(), user.getRole(), user.isOnboardingCompleted());
  }
}
