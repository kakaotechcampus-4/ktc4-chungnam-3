package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 브이월드(국토교통부) 검색 API(search2.0, type=place)로 프랜차이즈 지점의 정확한 좌표를 구한다 -
 * 공공 상가정보 반경검색의 기준점을 정밀하게 보정하는 용도로만 쓴다({@link FranchiseAnchorLookup}).
 * <p>
 * <b>이 응답은 어떤 필드도 저장하지 않는다.</b> 이용약관 제19조 6항("사전 승낙 없이 데이터를
 * 무단으로 저장하지 못한다")에 걸리지 않도록, 좌표는 {@link PlaceResolver}가 그 자리에서 공공
 * 상가정보 반경검색의 중심점으로 한 번 쓰고 버린다 - 실제로 Place에 저장되는 값은 항상 공공
 * 상가정보({@link DataportalStoreClient}) 자체 응답이다. 그래서 title도 반환하지 않는다(호출부가
 * 실수로 저장하는 걸 구조적으로 막기 위함).
 * <p>
 * 2026-10-06 실측 확인: "이디야커피 홍대청기와점"으로 검색하면 정확히 1건("이디야홍대청기와점",
 * 서울 마포구 월드컵북로 7)이 반환됨 - LocationIQ가 지오코딩 자체를 못 하던 매장인데도 VWorld는
 * 찾았다. 반대로 랜드마크·관광지·일부 맛집은 VWorld가 더 약해서(동명이인 상가·건물 노이즈가 심함)
 * 프랜차이즈 지점 전용으로만 쓴다.
 */
@Component
public class VWorldSearchClient implements FranchiseAnchorLookup {

    private static final String BASE_URL = "https://api.vworld.kr/req";

    private final RestClient restClient;
    private final String apiKey;

    public VWorldSearchClient(
            @Value("${vworld.api.key}") String apiKey,
            @Value("${vworld.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${vworld.read-timeout-ms:3000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseWrapper(Body response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(String status, Result result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Item(Point point) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Point(String x, String y) {
    }

    /**
     * 자유문장(브랜드+지점명 등)으로 검색해 1순위 후보의 좌표만 돌려준다. {@code status}가
     * {@code "NOT_FOUND"}거나 결과가 비어있으면 정상적인 "못 찾음"으로 보고 빈 값을 반환한다
     * (예외 아님 - PlaceLookup/NearbyStoreLookup과 같은 원칙).
     */
    @Override
    public Optional<PlaceSearchClient.PlaceSearchResult> findAnchor(String query) {
        ResponseWrapper response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                        .queryParam("service", "search")
                        .queryParam("request", "search")
                        .queryParam("version", "2.0")
                        .queryParam("size", 1)
                        .queryParam("page", 1)
                        .queryParam("query", query)
                        .queryParam("type", "place")
                        .queryParam("format", "json")
                        .queryParam("errorformat", "json")
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(ResponseWrapper.class);

        if (response == null || response.response() == null || !"OK".equals(response.response().status())) {
            return Optional.empty();
        }

        List<Item> items = response.response().result() == null || response.response().result().items() == null
                ? List.of() : response.response().result().items();
        if (items.isEmpty() || items.get(0).point() == null) {
            return Optional.empty();
        }

        Point point = items.get(0).point();
        return Optional.of(new PlaceSearchClient.PlaceSearchResult(
                null, Double.parseDouble(point.y()), Double.parseDouble(point.x()), null, null));
    }
}
