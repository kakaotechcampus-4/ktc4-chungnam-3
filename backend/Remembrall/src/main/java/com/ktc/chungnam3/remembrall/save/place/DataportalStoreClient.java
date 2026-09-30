package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소상공인시장진흥공단 상가(상권)정보 API(공공데이터포털, 서비스 15012005 / 제공기관 B553077)를
 * REST 직접 호출해, 좌표 반경 내 실제 등록 상가업소 목록을 확보한다. LocationIQ가 준 후보 좌표를
 * 여기 반경 조회에 넣고 상호명이 일치하는 업소가 있는지 확인하는 용도로 쓴다 - PlaceResolver의
 * 지역 교차검증이 시·도 단위까지만 가능한 한계(예: 「을지로 골뱅이」→잠실 오매칭)를 구·동 단위까지
 * 보완하기 위함(저장 파이프라인.md 개선②).
 * <p>
 * 오퍼레이션은 {@code storeListInDong}(행정동 코드 기준)이 아니라 {@code storeListInRadius}를 쓴다 -
 * {@code storeListInDong}의 {@code divId}가 받는 코드 체계를 실측으로 확인 못 했고(표준 행정동/법정동/
 * 시군구 코드 전부 거부됨, 이 API 자체의 내부 코드로 추정), 우리 용도(좌표 근처 검증)엔 좌표를
 * 직접 넣는 {@code storeListInRadius}가 애초에 더 맞기도 해서다(2026-09-28 실측, CLAUDE.md 참고).
 * <p>
 * base URL의 {@code sdsc2} 세그먼트를 주의: 문서·블로그에 흔한 {@code sdsc}(구버전)는 실제로
 * {@code NO_OPENAPI_SERVICE_ERROR}로 죽어있는 경로였고, 실측으로 {@code sdsc2}가 살아있는 경로임을
 * 확인했다.
 */
@Component
public class DataportalStoreClient implements NearbyStoreLookup {

    private static final String BASE_URL = "https://apis.data.go.kr/B553077/api/open/sdsc2";

    private final RestClient restClient;
    private final String apiKey;

    public DataportalStoreClient(@Value("${dataportal.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create(BASE_URL);
    }

    /** 도로명주소(rdnmAdr)를 기본으로 노출한다 - 지번주소보다 사용자에게 익숙한 표기라서. */
    public record StoreResult(String bizesNm, String brchNm, String category, String roadAddress,
                               double lat, double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoreListResponse(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(List<StoreItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoreItem(String bizesNm, String brchNm, String indsLclsNm, String rdnmAdr,
                              double lat, double lon) {
    }

    /**
     * 주어진 좌표 반경(미터) 안의 상가업소 목록을 반환한다.
     * {@code resultCode}가 {@code "03"}(NODATA_ERROR, 반경 내 업소 없음)이면 정상적인 빈 결과로 처리한다.
     * 그 외 정상(00)이 아닌 코드는 우리 쪽 요청 자체가 잘못됐다는 뜻이라 예외로 던진다 - 조용히
     * 빈 리스트를 돌려주면 "진짜 없음"과 "우리 코드 버그"를 구분할 수 없게 된다.
     * <p>
     * {@code numOfRows}를 1000으로 잡는다 - 강남역처럼 상권이 밀집한 곳은 반경 300m 안에도 상가업소가
     * 3천 건 넘게 있고, 응답 정렬 기준이 거리·관련도가 아니라 사실상 임의 순서라(실측 2026-09-30:
     * "이디야" 하나 찾으려고 1000건을 다 훑어야 했음), 적게 가져오면 찾는 브랜드가 통째로 안 보일 수
     * 있다. 그래도 극단적으로 밀집한 지역(강남역 반경 300m=3,424건)에선 부족할 수 있어서, 업종 필터를
     * 아는 경우엔 {@link #searchByRadius(double, double, int, List)}로 먼저 좁히는 쪽을 쓴다.
     */
    @Override
    public List<StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
        return call(lon, lat, radiusMeters, null);
    }

    /**
     * 업종 대분류 코드(예: "I2"=음식, "G2"=소매)로 서버 쪽에서 먼저 좁혀서 조회한다(2026-09-30 추가,
     * 실측: 강남역 반경 300m가 indsLclsCd 없이는 3,424건인데 "I2"만 걸면 538건으로 줄고, 그 안에서야
     * 찾던 브랜드가 보임). API가 코드를 하나만 받아서, 코드별로 나눠 호출한 뒤 상가업소번호(bizesId)
     * 기준으로 중복 제거해서 합친다. 코드 목록이 비어있으면 필터 없이 한 번만 호출한다.
     */
    @Override
    public List<StoreResult> searchByRadius(double lon, double lat, int radiusMeters, List<String> industryLargeCategoryCodes) {
        if (industryLargeCategoryCodes == null || industryLargeCategoryCodes.isEmpty()) {
            return searchByRadius(lon, lat, radiusMeters);
        }

        Map<String, StoreResult> merged = new LinkedHashMap<>();
        for (String categoryCode : industryLargeCategoryCodes) {
            for (StoreResult store : call(lon, lat, radiusMeters, categoryCode)) {
                merged.putIfAbsent(store.bizesNm() + "|" + store.roadAddress(), store);
            }
        }
        return List.copyOf(merged.values());
    }

    private List<StoreResult> call(double lon, double lat, int radiusMeters, String industryLargeCategoryCode) {
        StoreListResponse response = restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/storeListInRadius")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("type", "json")
                            .queryParam("numOfRows", 1000)
                            .queryParam("pageNo", 1)
                            .queryParam("cx", lon)
                            .queryParam("cy", lat)
                            .queryParam("radius", radiusMeters);
                    if (industryLargeCategoryCode != null) {
                        builder = builder.queryParam("indsLclsCd", industryLargeCategoryCode);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(StoreListResponse.class);

        if (response == null || response.header() == null) {
            throw new IllegalStateException("공공 상가정보 API 응답 형식이 예상과 다릅니다: " + response);
        }

        String resultCode = response.header().resultCode();
        if ("03".equals(resultCode)) {
            return List.of();
        }
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException(
                    "공공 상가정보 API 요청 실패 (resultCode=%s, resultMsg=%s)"
                            .formatted(resultCode, response.header().resultMsg()));
        }

        List<StoreItem> items = response.body() == null || response.body().items() == null
                ? List.of() : response.body().items();

        return items.stream()
                .map(item -> new StoreResult(
                        item.bizesNm(), item.brchNm(), item.indsLclsNm(), item.rdnmAdr(), item.lat(), item.lon()))
                .toList();
    }
}
