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

    /** osmClass/osmType은 도로("highway")·동네("place") 같은 비업체 결과를 걸러내는 데 쓴다 (PlaceResolver 참고). */
    public record PlaceSearchResult(String displayName, double lat, double lon, String osmClass, String osmType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocationIqItem(
            @JsonProperty("display_name") String displayName,
            String lat,
            String lon,
            @JsonProperty("class") String osmClass,
            String type) {
    }

    /**
     * 자유문장(q)으로 "이름 + 지역"만 검색한다 (지점명은 뺀다). 지점명을 검색어에 포함시켜봤는데
     * 도구 검토 문서(2026-09-23)에서 확인된 바로는, "본점" 같은 지점 표기가 지도 데이터에 거의 없어서
     * 그 단어 때문에 오히려 검색이 엉뚱한 방향으로 흔들리는 문제가 있었다. 그래서 LocationIQ에는
     * "이 이름의 업체가 이 지역에 뭐가 있는지"만 넓게 물어보고, 그중 어느 게 원하는 지점인지는
     * {@link PlaceResolver}가 반환된 후보들을 놓고 직접 비교해서 판단한다.
     * <p>
     * 구조화 쿼리(amenity/city)도 시도해봤으나, 개별 상점은 대부분 OSM에 amenity 태그로 안 잡혀있어서
     * 매칭에 실패하면 city 값 자체("서울", "대전" 같은 지명)를 엉뚱하게 반환해버리는 문제가 있었고,
     * regionHint가 없으면 400 Bad Request까지 났다 (실측 확인함). 그래서 자유문장 방식을 유지하되
     * countrycodes만 추가 - 이건 q와 같이 써도 되는 파라미터다.
     * 일치하는 장소가 없으면 빈 리스트를 반환한다 (정상 케이스, 예외 아님).
     */
    public List<PlaceSearchResult> search(String name, String branchName, String regionHint) {
        String query = buildQuery(name, regionHint);

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
                            Double.parseDouble(item.lon()),
                            item.osmClass(),
                            item.type()
                    ))
                    .toList();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        }
    }

    /**
     * regionHint 전체("서울 을지로"처럼 구·동까지 포함할 수 있음)를 그대로 검색어에 넣지 않고,
     * {@link KoreanProvinces}로 시·도만 뽑아서 넣는다. 구·동 단위 단어("을지로")는 "본점"과 마찬가지로
     * 지도 데이터에 잘 없어서 검색어에 섞이면 오히려 결과를 망칠 수 있다 - 시·도만으로도 지역 좁히기엔
     * 충분하고, 나머지 정밀도는 PlaceResolver의 지역 교차검증이 regionHint 전체를 보고 한 번 더 담당한다.
     * 시·도를 못 찾으면(매핑표에 없는 지역명 등) 지역 없이 이름만으로 검색한다.
     */
    private String buildQuery(String name, String regionHint) {
        StringBuilder query = new StringBuilder(name);
        KoreanProvinces.extract(regionHint).ifPresent(province -> query.append(' ').append(province));
        return query.toString();
    }
}
