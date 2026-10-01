package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
     */
    @Override
    public List<StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
        StoreListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/storeListInRadius")
                        .queryParam("serviceKey", "{serviceKey}")
                        .queryParam("type", "json")
                        .queryParam("numOfRows", 50)
                        .queryParam("pageNo", 1)
                        .queryParam("cx", lon)
                        .queryParam("cy", lat)
                        .queryParam("radius", radiusMeters)
                        .build(Map.of("serviceKey", apiKey)))
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
