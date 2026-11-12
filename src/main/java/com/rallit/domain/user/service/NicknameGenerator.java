package com.rallit.domain.user.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class NicknameGenerator {
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    var n = new StringBuilder("라켓");
    for (int i = 0; i < 8; i++)
      n.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
    return n.toString();
  }
}
