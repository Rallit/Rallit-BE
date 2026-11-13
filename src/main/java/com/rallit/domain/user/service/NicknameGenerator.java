package com.rallit.domain.user.service;

import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NicknameGenerator {
  private static final List<String> ADJECTIVES = List.of(
      "날쌘", "차분한", "씩씩한", "부지런한", "꾸준한", "든든한",
      "다정한", "활기찬", "유쾌한", "온화한", "명랑한", "재빠른",
      "느긋한", "꼼꼼한", "반짝이는", "당당한", "재치있는", "산뜻한",
      "경쾌한", "설레는", "침착한", "용감한", "따뜻한", "친절한");
  private static final List<String> TENNIS_TERMS = List.of(
      "라켓", "서브", "발리", "랠리", "스매시", "포핸드", "백핸드",
      "드롭샷", "로브", "슬라이스", "탑스핀", "리턴", "에이스",
      "매치포인트", "코트", "듀스", "베이스라인", "하프발리", "어프로치", "패싱샷");

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
    String term = TENNIS_TERMS.get(random.nextInt(TENNIS_TERMS.size()));
    int number = random.nextInt(100, 1000);
    return adjective + term + number;
  }
}
