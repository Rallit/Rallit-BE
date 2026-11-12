package com.rallit.domain.user.service;

import com.rallit.domain.region.repository.AreaRepository;
import com.rallit.domain.user.dto.NicknameAvailabilityResponse;
import com.rallit.domain.user.dto.OnboardingRequest;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.exception.UserErrorStatus;
import com.rallit.domain.user.repository.SocialAccountRepository;
import com.rallit.domain.user.repository.UserConstraintViolations;
import com.rallit.domain.user.repository.UserRepository;
import com.rallit.global.apiPayload.code.status.ErrorStatus;
import com.rallit.global.apiPayload.exception.GeneralException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository users;
  private final AreaRepository areas;
  private final SocialAccountRepository accounts;

  @Transactional(readOnly = true)
  public UserResponse getMe(Long userId) {
    return response(
        users.findById(userId).orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED)));
  }

  @Transactional(readOnly = true)
  public NicknameAvailabilityResponse checkNickname(Long userId, String nickname) {
    return new NicknameAvailabilityResponse(!users.isNicknameTakenByAnotherUser(nickname, userId));
  }

  @Transactional
  public UserResponse completeOnboarding(Long userId, OnboardingRequest request) {
    User user = users.findByIdForUpdate(userId).filter(current -> !current.isWithdrawn())
        .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
    var selectedAreas = areas.findAllByCodeIn(request.activityAreaCodes());
    if (selectedAreas.size() != request.activityAreaCodes().size()) {
      throw new GeneralException(UserErrorStatus.INVALID_ACTIVITY_AREA);
    }
    var profile = request.toProfile(Set.copyOf(selectedAreas));
    if (user.isOnboardingCompleted()) {
      if (user.hasOnboardingProfile(profile)) return response(user);
      throw new GeneralException(UserErrorStatus.ONBOARDING_ALREADY_COMPLETED);
    }
    if (users.isNicknameTakenByAnotherUser(profile.nickname(), userId)) {
      throw new GeneralException(UserErrorStatus.NICKNAME_CONFLICT);
    }
    user.completeOnboarding(profile);
    try {
      users.flush();
    } catch (DataIntegrityViolationException failure) {
      if (!UserConstraintViolations.isNicknameConflict(failure)) throw failure;
      throw new GeneralException(UserErrorStatus.NICKNAME_CONFLICT);
    }
    return response(user);
  }

  private UserResponse response(User user) {
    String displayName = accounts.findFirstByUserIdOrderByIdAsc(user.getId())
        .map(SocialAccount::getDisplayName).orElse(null);
    return UserResponse.from(user, displayName);
  }
}
