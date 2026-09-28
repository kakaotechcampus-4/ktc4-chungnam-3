package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * LocationIQ Forward Geocoding API를 API 키로 REST 직접 호출해 장소 후보의 좌표·주소를 확보한다.
 * 좌표 영구 저장이 허용되는 제공사로 선정했다 (Google Geocoding API는 좌표를 30일까지만 캐싱
 * 가능해, 지오펜스 트리거처럼 장기 보관이 필요한 용도엔 맞지 않음). 지도 "표시"는 별개로 Google
 * SDK를 쓴다 - 좌표 확보와 분리.
 */
@Component
public class PlaceSearchClient {

    private static final String BASE_URL = "https://us1.locationiq.com/v1";

    private final RestClient restClient;
    private final String apiKey;

    public PlaceSearchClient(@Value("${locationiq.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create(BASE_URL);
    }

    public record PlaceSearchResult(String displayName, double lat, double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocationIqItem(
            @JsonProperty("display_name") String displayName,
            String lat,
            String lon) {
    }

    /**
     * 자유문장(q)으로 검색한다. 구조화 쿼리(amenity/city)를 시도해봤으나, 개별 상점은 대부분
     * OSM에 amenity 태그로 안 잡혀있어서 매칭에 실패하면 city 값 자체("서울", "대전" 같은 지명)를
     * 엉뚱하게 반환해버리는 문제가 있었고, regionHint가 없으면 400 Bad Request까지 났다 (실측 확인함).
     * 그래서 자유문장 방식을 유지하되 countrycodes만 추가 - 이건 q와 같이 써도 되는 파라미터다.
     * 지역 오매칭 방어는 {@link PlaceResolver}의 regionHint 교차검증이 담당한다.
     * 일치하는 장소가 없으면 빈 리스트를 반환한다 (정상 케이스, 예외 아님).
     */
    public List<PlaceSearchResult> search(String name, String branchName, String regionHint) {
        String query = buildQuery(name, branchName, regionHint);

        try {
            List<LocationIqItem> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("key", apiKey)
                            .queryParam("q", query)
                            .queryParam("countrycodes", "kr")
                            .queryParam("format", "json")
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<LocationIqItem>>() {
                    });

            if (response == null) {
                return List.of();
            }

            return response.stream()
                    .map(item -> new PlaceSearchResult(
                            item.displayName(),
                            Double.parseDouble(item.lat()),
                            Double.parseDouble(item.lon())
                    ))
                    .toList();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        }
    }

    private String buildQuery(String name, String branchName, String regionHint) {
        StringBuilder query = new StringBuilder(name);
        if (branchName != null && !branchName.isBlank()) {
            query.append(' ').append(branchName);
        }
        if (regionHint != null && !regionHint.isBlank()) {
            query.append(' ').append(regionHint);
        }
        return query.toString();
    }
}
