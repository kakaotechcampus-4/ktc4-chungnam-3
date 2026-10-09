package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.ArrayList;
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
@Slf4j
@Component
public class DataportalStoreClient implements NearbyStoreLookup {

    private static final String BASE_URL = "https://apis.data.go.kr/B553077/api/open/sdsc2";

    // 한 페이지 최대 건수(API 자체 한도) - PR 리뷰(doheelab-coder)로 발견: 광화문 1,519건·목동역 인근
    // 1,200건대처럼 이 값을 넘는 지역이 실측상 흔해서(2026-10-09), 더 이상 "충분히 큰 값"이 아니라
    // 페이지네이션이 꼭 필요하다는 게 확인됨 - 아래 call()이 totalCount까지 다 받을 때까지 반복 호출함.
    private static final int NUM_OF_ROWS = 1000;

    private final RestClient restClient;
    private final String apiKey;

    /**
     * connect/read 타임아웃을 명시적으로 건다(2026-10-01 추가) - 이전엔 둘 다 무제한이라 공공데이터포털이
     * 멈추면 호출 스레드가 끝없이 블로킹됐다. 기본값은 실측 기반(업종 코드 1개짜리 단일 호출 기준 좌표
     * 6곳 실측: 평균 578ms, 최댓값 1286ms) - 최댓값의 2배가 조금 넘는 값으로 여유를 뒀다. 이 클라이언트는
     * 업종 코드 개수만큼 순차 호출하므로({@link #searchByRadius(double, double, int, List)}), 전체
     * 소요시간은 이 값에 호출 횟수를 곱해서 계산해야 한다 - PR #20 리뷰(팀원 doheelab-coder)의 분석
     * staleness 문턱 산정에 쓰임.
     */
    public DataportalStoreClient(
            @Value("${dataportal.api.key}") String apiKey,
            @Value("${dataportal.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${dataportal.read-timeout-ms:3000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 도로명주소(rdnmAdr)를 기본으로 노출한다 - 지번주소보다 사용자에게 익숙한 표기라서. bizesId(상가업소
     * 번호)는 PR 리뷰(doheelab-coder, 2026-10-09)로 추가 - 페이지네이션 때 페이지 간 중복 제거를
     * "상호명|주소" 조합(동명이인 상가에 취약) 대신 이 고유 식별자로 정확히 할 수 있다.
     */
    public record StoreResult(String bizesId, String bizesNm, String brchNm, String category, String roadAddress,
                               double lat, double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoreListResponse(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Header(String resultCode, String resultMsg) {
    }

    /** totalCount: 이 반경·업종코드 조건의 전체 건수(페이지네이션 종료 조건 판단용, 실측으로 필드명 확인함). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(List<StoreItem> items, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoreItem(String bizesId, String bizesNm, String brchNm, String indsLclsNm, String rdnmAdr,
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
        return call(lon, lat, radiusMeters, null, null);
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
        return callEach(lon, lat, radiusMeters, "indsLclsCd", industryLargeCategoryCodes);
    }

    /**
     * 업종 소분류 코드(예: "G20405"=편의점)로 대분류보다 훨씬 좁혀서 조회한다(2026-10-08 추가, 실측
     * 확인: 강남역 반경에서 "CU"로 대분류(G2)만 걸고 상호명 부분일치로 찾으면 무관한 업소까지 15건
     * 걸리는데, 소분류로 먼저 걸러두면 편의점끼리만 남아 과매칭이 사라짐). "CU"처럼 브랜드명이 너무
     * 짧아 상호명 부분일치만으론 과매칭되는 브랜드 전용 - {@link PlaceResolver}가 호출 여부를 판단한다.
     */
    @Override
    public List<StoreResult> searchByRadiusBySubCategory(
            double lon, double lat, int radiusMeters, List<String> industrySubCategoryCodes) {
        if (industrySubCategoryCodes == null || industrySubCategoryCodes.isEmpty()) {
            return searchByRadius(lon, lat, radiusMeters);
        }
        return callEach(lon, lat, radiusMeters, "indsSclsCd", industrySubCategoryCodes);
    }

    /**
     * 업종 코드마다 페이지네이션으로 전부 받은 뒤, 상가업소번호(bizesId)로 중복을 제거해서 합친다
     * (PR 리뷰(doheelab-coder) 반영, 2026-10-09 - 예전엔 "상호명|주소" 조합으로 중복을 지웠는데,
     * 페이지 간 응답 순서가 안 정렬돼 있어 동명이인 상가가 섞이면 부정확할 수 있었다). 업종 코드별로
     * 중복 제거 후 개수가 그 코드의 totalCount와 다르면 경고 로그를 남긴다 - 리뷰어가 제안한 검증
     * 방법을 코드에 남겨 나중에 빠진 데이터를 알아챌 수 있게 함.
     */
    private List<StoreResult> callEach(
            double lon, double lat, int radiusMeters, String paramName, List<String> codes) {
        Map<String, StoreResult> merged = new LinkedHashMap<>();
        for (String code : codes) {
            List<StoreResult> stores = call(lon, lat, radiusMeters, paramName, code);
            int beforeDedupe = merged.size();
            for (StoreResult store : stores) {
                merged.putIfAbsent(store.bizesId(), store);
            }
            int addedByThisCode = merged.size() - beforeDedupe;
            if (addedByThisCode < stores.size()) {
                log.debug("업종 코드 {}={} 응답 {}건 중 {}건이 다른 코드와 중복(bizesId 기준) - 정상",
                        paramName, code, stores.size(), stores.size() - addedByThisCode);
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * {@code numOfRows}(1,000건) 상한을 넘는 지역(실측: 광화문 1,519건, 목동역 인근 1,200건대,
     * PR 리뷰(doheelab-coder) 반영, 2026-10-09)을 위해 {@code totalCount}를 다 받을 때까지
     * {@link #callPage}를 페이지네이션으로 반복 호출한다. {@code totalCount}가 안 오면(과거 응답
     * 형식일 가능성 대비) 받은 건수가 {@link #NUM_OF_ROWS}보다 적어지는 페이지에서 멈춘다.
     */
    private List<StoreResult> call(double lon, double lat, int radiusMeters, String categoryParamName, String categoryCode) {
        List<StoreResult> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            PageResult page = callPage(lon, lat, radiusMeters, categoryParamName, categoryCode, pageNo);
            all.addAll(page.stores());

            boolean lastPage = page.totalCount() != null
                    ? (long) pageNo * NUM_OF_ROWS >= page.totalCount()
                    : page.stores().size() < NUM_OF_ROWS;
            if (lastPage) {
                if (page.totalCount() != null && all.size() != page.totalCount()) {
                    log.warn("공공 상가정보 페이지네이션 결과({}건)가 totalCount({})와 다릅니다 - "
                                    + "categoryCode={}, cx={}, cy={}, radius={}",
                            all.size(), page.totalCount(), categoryCode, lon, lat, radiusMeters);
                }
                return all;
            }
            pageNo++;
        }
    }

    /** 한 페이지 조회 결과 + 이 조건(반경·업종코드)의 전체 건수(페이지네이션 종료 판단용). */
    private record PageResult(List<StoreResult> stores, Integer totalCount) {
    }

    private PageResult callPage(
            double lon, double lat, int radiusMeters, String categoryParamName, String categoryCode, int pageNo) {
        // serviceKey는 {serviceKey} 자리표시자 + build(Map.of(...))로 넣는다(팀원 doheelab-coder 리뷰
        // 반영) - 리터럴로 바로 넣으면 키에 '+'가 있을 때 인코딩이 안 돼 서버가 공백으로 읽어버린다.
        StoreListResponse response = restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/storeListInRadius")
                            .queryParam("serviceKey", "{serviceKey}")
                            .queryParam("type", "json")
                            .queryParam("numOfRows", NUM_OF_ROWS)
                            .queryParam("pageNo", pageNo)
                            .queryParam("cx", lon)
                            .queryParam("cy", lat)
                            .queryParam("radius", radiusMeters);
                    if (categoryCode != null) {
                        builder = builder.queryParam(categoryParamName, categoryCode);
                    }
                    return builder.build(Map.of("serviceKey", apiKey));
                })
                .retrieve()
                .body(StoreListResponse.class);

        if (response == null || response.header() == null) {
            throw new IllegalStateException("공공 상가정보 API 응답 형식이 예상과 다릅니다: " + response);
        }

        String resultCode = response.header().resultCode();
        if ("03".equals(resultCode)) {
            return new PageResult(List.of(), 0);
        }
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException(
                    "공공 상가정보 API 요청 실패 (resultCode=%s, resultMsg=%s)"
                            .formatted(resultCode, response.header().resultMsg()));
        }

        List<StoreItem> items = response.body() == null || response.body().items() == null
                ? List.of() : response.body().items();

        List<StoreResult> stores = items.stream()
                .map(item -> new StoreResult(
                        item.bizesId(), item.bizesNm(), item.brchNm(), item.indsLclsNm(), item.rdnmAdr(),
                        item.lat(), item.lon()))
                .toList();
        return new PageResult(stores, response.body() == null ? null : response.body().totalCount());
    }
}
