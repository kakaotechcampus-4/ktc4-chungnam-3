package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
public class PlaceSearchClient implements PlaceLookup {

    private static final String BASE_URL = "https://us1.locationiq.com/v1";

    private final RestClient restClient;
    private final String apiKey;

    /**
     * connect/read 타임아웃을 명시적으로 건다(2026-10-01 추가) - 이전엔 둘 다 무제한이라 LocationIQ가
     * 멈추면 호출 스레드가 끝없이 블로킹됐다. 기본값은 실측 기반(랜드마크·교통시설·검증된 프랜차이즈
     * 지점 15개 쿼리 실측: 평균 589ms, p95/최댓값 1252ms) - 최댓값의 2배가 조금 넘는 값으로 여유를
     * 뒀다. PR #20 리뷰(팀원 doheelab-coder)의 "호출마다 타임아웃을 먼저 걸고, 분석 전체의 staleness
     * 문턱은 그 합보다 조금 긴 값으로 잡으라"는 제안에 쓰일 실측값이기도 하다.
     */
    public PlaceSearchClient(
            @Value("${locationiq.api.key}") String apiKey,
            @Value("${locationiq.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${locationiq.read-timeout-ms:3000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
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
     * <p>
     * {@code accept-language=ko}도 추가한다 (2026-09-29 실측) - 이게 없으면 Nominatim이 장소의
     * 여러 다국어 이름 태그 중 기본값(유명 장소는 보통 영문 {@code name})을 돌려줘서, 한글 검색어와
     * displayName이 아예 안 겹쳐 {@link PlaceResolver}의 이름 필터에서 통째로 걸러지는 문제가 있었다
     * (예: "경복궁" 검색 → {@code Gyeongbokgung Palace}만 돌아옴). {@code ko}를 지정하면 {@code name:ko}
     * 태그가 있는 장소는 한글로 옴 - 단, 일부는 여전히 정식 명칭이라 구어체 검색어와 다를 수 있음
     * (예: "명동성당" 검색해도 {@code name:ko}가 "명동대성당"이라 부분일치 안 될 수 있음, 별도 이슈).
     * <p>
     * 일치하는 장소가 없으면 빈 리스트를 반환한다 (정상 케이스, 예외 아님).
     */
    @Override
    public List<PlaceSearchResult> search(String name, String branchName, String regionHint) {
        String query = buildQuery(name, regionHint);

        try {
            List<LocationIqItem> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("key", apiKey)
                            .queryParam("q", query)
                            .queryParam("countrycodes", "kr")
                            .queryParam("accept-language", "ko")
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
     * <p>
     * {@code name}에 시·도명이 이미 들어있으면(도로명주소를 통째로 넣는 경우 흔함, 예: "서울 마포구
     * 월드컵북로 7") 중복 추가하지 않는다(2026-10-02 수정) - 실측 확인: "서울 마포구 월드컵북로 7"은
     * LocationIQ가 후보를 반환하지만, 그 뒤에 "서울"을 한 번 더 붙인 "서울 마포구 월드컵북로 7 서울"은
     * {@code Unable to geocode}로 완전히 실패한다. 지역명이 중복되면 쿼리 자체가 깨지는 것으로 보인다.
     */
    String buildQuery(String name, String regionHint) {
        StringBuilder query = new StringBuilder(name);
        KoreanProvinces.extract(regionHint)
                .filter(province -> !name.contains(province))
                .ifPresent(province -> query.append(' ').append(province));
        return query.toString();
    }
}
