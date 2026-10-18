package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 지방행정인허가 통합 OpenAPI(제공기관 1741000, 행정안전부) 실제 호출 구현체. 전국을 통째로 받지 않고
 * -- {@code cond[BPLC_NM::LIKE]} 서버 필터로 {@link #TARGET_BRANDS}만 가져온다(2026-10-08 실측으로
 * 확인된 콜드스타트 회피 방법 - 업종 하나가 전국 230만 건이라 전체 적재는 비현실적).
 * <p>
 * 좌표(`CRD_INFO_X/Y`)는 EPSG:5174(Bessel 중부원점 TM)로 와서, WGS84(EPSG:4326)로 변환해야 한다 -
 * 손으로 투영식을 짜면 틀리기 쉬워서 proj4j로 변환한다.
 */
@Slf4j
@Component
public class BusinessLicenseGovClient implements BusinessLicenseSource {

    // 2026-10-08 실측 확인된 베이스 URL - 둘 다 파라미터 체계(cond[] 필터 포함)가 동일함.
    private static final List<String> BASE_URLS = List.of(
            "https://apis.data.go.kr/1741000/general_restaurants",
            "https://apis.data.go.kr/1741000/rest_cafes"
    );
    // 공공상가정보·VWorld로 못 풀던 대형 직영 체인만 좁혀서 가져온다(place-resolution-history 스킬,
    // GitHub 이슈 #15 참고) - CU는 매칭 로직(지역 교차검증)으로 별도 해결됨(PlaceResolver 참고). 다이소는
    // 일반 소매업이라 이 두 베이스 URL(음식점·카페 인허가)엔 애초에 등록 대상이 아니라서 뺐다
    // (2026-10-08 실측 확인 - 0건 아니라 "이 데이터셋 자체가 다루는 업종이 아님").
    private static final List<String> TARGET_BRANDS = List.of("맥도날드", "스타벅스");
    private static final String OPERATING_STATUS_CODE = "01";
    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient restClient;
    private final String apiKey;
    private final CoordinateTransform toWgs84;

    public BusinessLicenseGovClient(
            @Value("${dataportal.api.key}") String apiKey,
            @Value("${dataportal.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${dataportal.read-timeout-ms:5000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        // proj4j 내장 EPSG 레지스트리 파일 접근이 이 환경에서 실패해서(IllegalStateException: Unable to
        // access CRS file), 레지스트리 조회 대신 epsg.io/5174의 공식 proj4 파라미터를 직접 지정한다.
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem epsg5174 = crsFactory.createFromParameters("EPSG:5174",
                "+proj=tmerc +lat_0=38 +lon_0=127.002890277778 +k=1 +x_0=200000 +y_0=500000 "
                        + "+ellps=bessel +towgs84=-145.907,505.034,685.756,-1.162,2.347,1.592,6.342 "
                        + "+units=m +no_defs");
        CoordinateReferenceSystem epsg4326 = crsFactory.createFromParameters("EPSG:4326", "+proj=longlat +datum=WGS84 +no_defs");
        this.toWgs84 = new CoordinateTransformFactory().createTransform(epsg5174, epsg4326);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseWrapper(Body response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(Header header, Items body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Items(ItemList items, int numOfRows, int pageNo, long totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemList(List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Item(
            String MNG_NO, String BPLC_NM, String SALS_STTS_NM, String BZSTAT_SE_NM,
            String ROAD_NM_ADDR, String LOTNO_ADDR, String CRD_INFO_X, String CRD_INFO_Y) {
    }

    @Override
    public List<BusinessLicenseRecord> fetchAll() {
        return fetch(null);
    }

    @Override
    public List<BusinessLicenseRecord> fetchChangesSince(LocalDate since) {
        return fetch(since.atStartOfDay().format(TIMESTAMP_FORMAT));
    }

    private List<BusinessLicenseRecord> fetch(String updatedSince) {
        List<BusinessLicenseRecord> records = new ArrayList<>();
        for (String baseUrl : BASE_URLS) {
            for (String brand : TARGET_BRANDS) {
                records.addAll(fetchByBrand(baseUrl, brand, updatedSince));
            }
        }
        return records;
    }

    private List<BusinessLicenseRecord> fetchByBrand(String baseUrl, String brand, String updatedSince) {
        List<BusinessLicenseRecord> records = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            Items page = callPage(baseUrl, brand, updatedSince, pageNo);
            if (page == null || page.items() == null || page.items().item() == null || page.items().item().isEmpty()) {
                break;
            }
            for (Item item : page.items().item()) {
                toRecord(item).ifPresent(records::add);
            }
            if ((long) pageNo * PAGE_SIZE >= page.totalCount()) {
                break;
            }
            pageNo++;
        }
        return records;
    }

    private Items callPage(String baseUrl, String brand, String updatedSince, int pageNo) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl + "/info")
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("returnType", "json")
                .queryParam("cond[BPLC_NM::LIKE]", "{brand}");
        // 델타 동기화(updatedSince != null)에 영업상태 필터까지 같이 걸면, 폐업으로 막 바뀐 행이
        // "영업중 아님"이라 조회 자체에서 걸러져 폐업 감지를 영영 못 한다(리뷰 반영) - 델타일 땐
        // 변경분 조건만, 최초 전체 적재일 땐 영업중 조건만 건다.
        if (updatedSince == null) {
            uri.queryParam("cond[SALS_STTS_CD::EQ]", OPERATING_STATUS_CODE);
        } else {
            uri.queryParam("cond[DAT_UPDT_PNT::GTE]", "{updatedSince}");
        }

        ResponseWrapper response;
        try {
            response = restClient.get()
                    .uri(uri.encode().buildAndExpand(apiKey, brand, updatedSince).toUri())
                    .retrieve()
                    .body(ResponseWrapper.class);
        } catch (RuntimeException e) {
            log.warn("지방행정인허가 호출 실패 baseUrl={} brand={} pageNo={}: {}", baseUrl, brand, pageNo, e.getMessage());
            return null;
        }

        if (response == null || response.response() == null || response.response().header() == null
                || !"0".equals(response.response().header().resultCode())) {
            return null;
        }
        return response.response().body();
    }

    private java.util.Optional<BusinessLicenseRecord> toRecord(Item item) {
        Double lat = null;
        Double lon = null;
        try {
            if (item.CRD_INFO_X() != null && item.CRD_INFO_Y() != null
                    && !item.CRD_INFO_X().isBlank() && !item.CRD_INFO_Y().isBlank()) {
                ProjCoordinate source = new ProjCoordinate(
                        Double.parseDouble(item.CRD_INFO_X()), Double.parseDouble(item.CRD_INFO_Y()));
                ProjCoordinate target = new ProjCoordinate();
                toWgs84.transform(source, target);
                lon = target.x;
                lat = target.y;
            }
        } catch (RuntimeException e) {
            log.warn("좌표 변환 실패 MNG_NO={}: {}", item.MNG_NO(), e.getMessage());
        }

        String address = item.ROAD_NM_ADDR() != null && !item.ROAD_NM_ADDR().isBlank()
                ? item.ROAD_NM_ADDR() : item.LOTNO_ADDR();

        return java.util.Optional.of(new BusinessLicenseRecord(
                item.MNG_NO(), item.BPLC_NM(), item.BZSTAT_SE_NM(), item.SALS_STTS_NM(), address, lat, lon));
    }
}
