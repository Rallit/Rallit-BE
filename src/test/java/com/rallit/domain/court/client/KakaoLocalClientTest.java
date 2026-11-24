package com.rallit.domain.court.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rallit.global.config.KakaoLocalProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class KakaoLocalClientTest {
  private static final String KEYWORD_PATH = "/v2/local/search/keyword.json";
  private static final String REGION_PATH = "/v2/local/geo/coord2regioncode.json";
  private static final AtomicReference<Reply> reply = new AtomicReference<>();
  private static final AtomicReference<HttpExchange> lastRequest = new AtomicReference<>();
  private static HttpServer server;

  private final KakaoLocalClient client = client("test-rest-key");

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/", exchange -> {
      lastRequest.set(exchange);
      var current = reply.get();
      try {
        Thread.sleep(current.delay().toMillis());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
      exchange.sendResponseHeaders(current.status(), body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void resetReply() {
    reply.set(new Reply(200, "{}", Duration.ZERO));
    lastRequest.set(null);
  }

  @Test
  void searchesNearbyPlacesWithEncodedKeywordAndCoordinatesInKakaoOrder() {
    reply.set(new Reply(200, """
        {
          "meta": {"total_count": 120, "pageable_count": 45, "is_end": false, "same_name": null},
          "documents": [
            {"id": "8152837", "place_name": "장충테니스장", "category_name": "스포츠,레저 > 테니스장",
             "address_name": "서울 중구 장충동2가 산 14-49", "road_address_name": "서울 중구 동호로 241",
             "x": "127.00437712", "y": "37.55865123", "distance": "120", "phone": "", "place_url": "http://x"},
            {"id": "999", "place_name": "동네 테니스장", "category_name": "스포츠,레저 > 테니스장",
             "address_name": "서울 중구 신당동 1", "road_address_name": "", "x": "127.01", "y": "37.56",
             "distance": "850"}
          ]
        }
        """, Duration.ZERO));

    var page = client.searchKeyword(new KakaoLocalClient.KeywordSearch("장충 테니스&장+", new BigDecimal("37.558651"),
        new BigDecimal("127.004377"), 5000, true, 2, 15));

    assertThat(page.pageableCount()).isEqualTo(45);
    assertThat(page.end()).isFalse();
    assertThat(page.places()).extracting(KakaoLocalClient.Place::id).containsExactly("8152837", "999");
    var first = page.places().getFirst();
    assertThat(first.name()).isEqualTo("장충테니스장");
    assertThat(first.address()).isEqualTo("서울 중구 동호로 241");
    assertThat(first.latitude()).isEqualByComparingTo("37.558651");
    assertThat(first.longitude()).isEqualByComparingTo("127.004377");
    assertThat(first.distanceMeters()).isEqualTo(120);
    assertThat(page.places().get(1).address()).isEqualTo("서울 중구 신당동 1");

    var request = lastRequest.get();
    assertThat(request.getRequestURI().getPath()).isEqualTo(KEYWORD_PATH);
    assertThat(request.getRequestHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-rest-key");
    assertThat(query(request)).containsExactlyInAnyOrderEntriesOf(Map.of("query", "장충 테니스&장+", "page", "2",
        "size", "15", "sort", "distance", "x", "127.004377", "y", "37.558651", "radius", "5000"));
  }

  @Test
  void searchesByKeywordOnlyWithoutLocationAndReturnsAnEmptyPage() {
    reply.set(new Reply(200, """
        {"meta": {"total_count": 0, "pageable_count": 0, "is_end": true}, "documents": []}
        """, Duration.ZERO));

    var page = client.searchKeyword(new KakaoLocalClient.KeywordSearch("없는코트", null, null, null, false, 1, 15));

    assertThat(page.places()).isEmpty();
    assertThat(page.end()).isTrue();
    assertThat(query(lastRequest.get())).containsOnlyKeys("query", "page", "size", "sort")
        .containsEntry("sort", "accuracy");
  }

  @Test
  void returnsTheLegalDistrictCode() {
    reply.set(new Reply(200, """
        {"documents": [
          {"region_type": "H", "code": "1114059000"},
          {"region_type": "B", "code": "1114016200"}
        ]}
        """, Duration.ZERO));

    assertThat(client.legalDistrictCode(new BigDecimal("37.558651"), new BigDecimal("127.004377")))
        .contains("1114016200");
    assertThat(lastRequest.get().getRequestURI().getPath()).isEqualTo(REGION_PATH);
    assertThat(query(lastRequest.get())).containsEntry("x", "127.004377").containsEntry("y", "37.558651");
  }

  @Test
  void treatsKakaoErrorsAndMalformedResponsesAsUnavailable() {
    var search = new KakaoLocalClient.KeywordSearch("장충", null, null, null, false, 1, 15);
    for (var failure : new Reply[] {
        new Reply(500, "{\"errorType\":\"InternalServerError\"}", Duration.ZERO),
        new Reply(429, "{\"errorType\":\"RequestThrottled\"}", Duration.ZERO),
        new Reply(401, "{\"errorType\":\"AccessDeniedError\"}", Duration.ZERO),
        new Reply(200, "{\"documents\": []}", Duration.ZERO),
        new Reply(200, "{\"meta\": {\"pageable_count\": 1, \"is_end\": true}, \"documents\": [{\"id\": \"1\"}]}",
            Duration.ZERO)}) {
      reply.set(failure);
      assertThatThrownBy(() -> client.searchKeyword(search))
          .isInstanceOf(KakaoLocalClient.UnavailableException.class)
          .hasMessageNotContaining("test-rest-key");
    }
  }

  @Test
  void timesOutSlowResponses() {
    reply.set(new Reply(200, "{}", Duration.ofSeconds(2)));

    assertThatThrownBy(() -> client.legalDistrictCode(BigDecimal.ONE, BigDecimal.ONE))
        .isInstanceOf(KakaoLocalClient.UnavailableException.class)
        .hasCauseInstanceOf(ResourceAccessException.class)
        .hasRootCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void refusesToCallKakaoWithoutAKey() {
    var unconfigured = client(" ");

    assertThatThrownBy(() -> unconfigured.searchKeyword(
        new KakaoLocalClient.KeywordSearch("장충", null, null, null, false, 1, 15)))
        .isInstanceOf(KakaoLocalClient.UnavailableException.class);
    assertThat(lastRequest.get()).isNull();
  }

  private static KakaoLocalClient client(String key) {
    return new KakaoLocalClient(new KakaoLocalProperties(key,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(1),
        Duration.ofMillis(500), 30));
  }

  private static Map<String, String> query(HttpExchange exchange) {
    return Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1])));
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private record Reply(int status, String body, Duration delay) {}
}
