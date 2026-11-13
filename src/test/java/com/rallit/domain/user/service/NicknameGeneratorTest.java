package com.rallit.domain.user.service;

import static com.rallit.domain.user.entity.OnboardingProfile.NICKNAME_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NicknameGeneratorTest {
  @Test
  void generatesKoreanNicknameWithThreeDigitsWithinOnboardingRules() {
    var generator = new NicknameGenerator();
    var nicknames = IntStream.range(0, 100).mapToObj(index -> generator.generate()).toList();

    assertThat(nicknames).allSatisfy(nickname -> assertThat(nickname)
        .matches("[가-힣]+[1-9][0-9]{2}")
        .matches(NICKNAME_PATTERN));
  }
}
