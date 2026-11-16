package com.rallit.domain.user.entity;

import java.math.BigDecimal;

public final class Ntrp {

  private static final BigDecimal UPPER_BAND = BigDecimal.valueOf(4);
  private static final BigDecimal STEP = new BigDecimal("0.5");

  private Ntrp() {
  }

  public static boolean isSupported(BigDecimal value) {
    return value != null
        && value.compareTo(BigDecimal.ONE) >= 0
        && value.compareTo(UPPER_BAND) <= 0
        && value.remainder(STEP).signum() == 0;
  }
}
