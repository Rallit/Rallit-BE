package com.rallit.domain.court.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rallit.global.config.KakaoLocalProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoLocalClient {
  private static final String KEYWORD_PATH = "/v2/local/search/keyword.json";
  private static final String REGION_PATH = "/v2/local/geo/coord2regioncode.json";
  private static final String LEGAL_DISTRICT = "B";
  private static final int COORDINATE_SCALE = 6;

  private final RestClient client;
  private final boolean configured;

  public KakaoLocalClient(KakaoLocalProperties properties) {
    var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(properties.readTimeout());
    this.configured = StringUtils.hasText(properties.restApiKey());
    this.client = RestClient.builder()
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(requests)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
        .build();
  }

  public PlacePage searchKeyword(KeywordSearch search) {
    var parameters = new LinkedHashMap<String, Object>();
    parameters.put("query", search.query());
    parameters.put("page", search.page());
    parameters.put("size", search.size());
    parameters.put("sort", search.sortByDistance() ? "distance" : "accuracy");
    if (search.latitude() != null) {
      parameters.put("x", search.longitude().toPlainString());
      parameters.put("y", search.latitude().toPlainString());
      parameters.put("radius", search.radiusMeters());
    }
    var response = get(KEYWORD_PATH, parameters, KeywordResponse.class);
    if (response.meta() == null || response.documents() == null) {
      throw new UnavailableException("Kakao keyword search returned an incomplete response", null);
    }
    return new PlacePage(response.documents().stream().map(KakaoLocalClient::place).toList(),
        response.meta().pageableCount(), response.meta().end());
  }

  public Optional<String> legalDistrictCode(BigDecimal latitude, BigDecimal longitude) {
    var response = get(REGION_PATH, Map.of("x", longitude.toPlainString(), "y", latitude.toPlainString()),
        RegionResponse.class);
    if (response.documents() == null) {
      throw new UnavailableException("Kakao region lookup returned an incomplete response", null);
    }
    return response.documents().stream().filter(region -> LEGAL_DISTRICT.equals(region.regionType()))
        .map(RegionDocument::code).filter(StringUtils::hasText).findFirst();
  }

  private <T> T get(String path, Map<String, ?> parameters, Class<T> type) {
    if (!configured) {
      throw new UnavailableException("Kakao REST API key is not configured", null);
    }
    try {
      T body = client.get().uri(uri -> {
        uri.path(path);
        parameters.keySet().forEach(name -> uri.queryParam(name, "{" + name + "}"));
        return uri.build(parameters);
      }).retrieve().body(type);
      if (body == null) {
        throw new UnavailableException("Kakao Local API returned an empty body", null);
      }
      return body;
    } catch (RestClientException failure) {
      throw new UnavailableException("Kakao Local API request failed", failure);
    }
  }

  private static Place place(PlaceDocument document) {
    if (!StringUtils.hasText(document.id()) || !StringUtils.hasText(document.placeName())
        || document.x() == null || document.y() == null) {
      throw new UnavailableException("Kakao Local API returned an incomplete place", null);
    }
    try {
      return new Place(document.id(), document.placeName(), document.roadAddressName(), document.addressName(),
          coordinate(document.y()), coordinate(document.x()),
          StringUtils.hasText(document.distance()) ? Integer.valueOf(document.distance()) : null,
          document.categoryName());
    } catch (NumberFormatException invalid) {
      throw new UnavailableException("Kakao Local API returned an invalid place", invalid);
    }
  }

  private static BigDecimal coordinate(String value) {
    return new BigDecimal(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
  }

  public record KeywordSearch(String query, BigDecimal latitude, BigDecimal longitude, Integer radiusMeters,
      boolean sortByDistance, int page, int size) {}

  public record PlacePage(List<Place> places, int pageableCount, boolean end) {}

  public record Place(String id, String name, String roadAddress, String jibunAddress, BigDecimal latitude,
      BigDecimal longitude, Integer distanceMeters, String categoryName) {
    public String address() {
      return StringUtils.hasText(roadAddress) ? roadAddress : jibunAddress;
    }
  }

  public static class UnavailableException extends RuntimeException {
    public UnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record KeywordResponse(Meta meta, List<PlaceDocument> documents) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Meta(@JsonProperty("pageable_count") int pageableCount, @JsonProperty("is_end") boolean end) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PlaceDocument(String id, @JsonProperty("place_name") String placeName,
      @JsonProperty("category_name") String categoryName, @JsonProperty("address_name") String addressName,
      @JsonProperty("road_address_name") String roadAddressName, String x, String y, String distance) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RegionResponse(List<RegionDocument> documents) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RegionDocument(@JsonProperty("region_type") String regionType, String code) {}
}
