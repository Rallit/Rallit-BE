package com.rallit.global.security.principal;

import com.rallit.domain.auth.model.LoginTarget;
import com.rallit.domain.user.enums.UserRole;

public record RallitPrincipal(
    Long userId, LoginTarget target, UserRole role, boolean onboardingCompleted) {}
