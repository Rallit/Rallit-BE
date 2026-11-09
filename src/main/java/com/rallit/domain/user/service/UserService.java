package com.rallit.domain.user.service;

import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository users;

  @Transactional(readOnly = true)
  public UserResponse getMe(Long userId) {
    return UserResponse.from(
        users.findById(userId).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED)));
  }
}
