package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국관광공사 국문관광정보 서비스_GW(data.go.kr, {@code KorService2/searchKeyword2})로 지점명 없는
 * 일반 장소(공원·관광지 등)의 공식 명칭·주소·좌표를 검색한다 - {@link GeneralPlaceAnchorLookup} 구현체.
 * <p>
 * data.go.kr "이용허락범위 제한 없음"으로 확인함(2026-10-28, {@code
 * https://www.data.go.kr/data/15101578/openapi.do}) - {@link VWorldSearchClient}와 달리 응답을 그대로
 * 저장해도 되는 소스다. {@code contentTypeId=12}(관광지)로 서버 쪽에서 미리 좁혀서, 같은 이름을 포함한
 * 식당·쇼핑 업소(예: "경복궁식당")가 섞여 들어오는 걸 API 단에서 막는다.
 * <p>
 * {@code serviceKey}는 공공데이터포털(dataportal) 쪽과 계정 단위로 공용이라({@code
 * tourapi.api.key}가 아직 승인 전이어도 {@code dataportal.api.key}가 그대로 통함, 실측 확인됨) -
 * {@code application.yaml}의 {@code tourapi.api.key} 프로퍼티는 비워두고 운영 중엔 dataportal 키를
 * 그대로 써도 된다. 이중 인코딩을 피하려고 URI를 문자열로 직접 조립한다(TourApiRawFieldCheckScratchTest에서
 * 실측으로 확인한 함정과 동일).
 * <p>
 * 응답이 0건이면 {@code body.items}가 객체가 아니라 빈 문자열({@code ""})로 온다(실측 확인, 2026-10-28) -
 * {@code item}을 레코드로 바로 매핑하면 역직렬화가 깨지므로 {@link JsonNode}로 받아 방어적으로 다룬다.
 */
@Component
public class TourApiClient implements GeneralPlaceAnchorLookup {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2/searchKeyword2";
    private static final String CONTENT_TYPE_ID_TOURIST_SPOT = "12";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public TourApiClient(
            @Value("${tourapi.api.key}") String serviceKey,
            @Value("${tourapi.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${tourapi.read-timeout-ms:3000}") int readTimeoutMs,
            ObjectMapper objectMapper) {
        this.serviceKey = serviceKey;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** contentId는 TourAPI 자체 콘텐츠 식별자 - {@link com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider#TOURAPI}의 geocodingPlaceId로 쓰인다. */
    public record TourApiItem(String contentId, String title, String address, double lat, double lon) {
    }

    @Override
    public List<TourApiItem> search(String name) {
        String uri = BASE_URL
                + "?serviceKey=" + serviceKey
                + "&MobileOS=ETC"
                + "&MobileApp=Remembrall"
                + "&_type=json"
                + "&numOfRows=20"
                + "&pageNo=1"
                + "&contentTypeId=" + CONTENT_TYPE_ID_TOURIST_SPOT
                + "&keyword=" + URLEncoder.encode(name, StandardCharsets.UTF_8);

        String raw = restClient.get().uri(URI.create(uri)).retrieve().body(String.class);
        return parseItems(raw);
    }

    /**
     * 응답 본문 파싱만 떼어낸 패키지 전용 메서드(테스트 전용, {@link PlaceSearchClient#buildQuery}와
     * 같은 이유 - 실제 HTTP 호출 없이 순수 로직만 검증). {@code resultCode}가 {@code "0000"}이 아니면
     * 우리 쪽 요청 자체가 잘못됐다는 뜻이라 예외로 던진다({@link DataportalStoreClient}와 같은 원칙).
     */
    List<TourApiItem> parseItems(String raw) {
        JsonNode response = parseJson(raw).path("response");
        String resultCode = response.path("header").path("resultCode").asText();
        if (!"0000".equals(resultCode)) {
            throw new IllegalStateException("TourAPI 요청 실패 (resultCode=%s, resultMsg=%s)"
                    .formatted(resultCode, response.path("header").path("resultMsg").asText()));
        }

        JsonNode itemNode = response.path("body").path("items").path("item");
        if (!itemNode.isArray()) {
            return List.of();
        }

        List<TourApiItem> items = new ArrayList<>();
        for (JsonNode item : itemNode) {
            items.add(new TourApiItem(
                    item.path("contentid").asText(),
                    item.path("title").asText(),
                    item.path("addr1").asText(),
                    item.path("mapy").asDouble(),
                    item.path("mapx").asDouble()));
        }
        return items;
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("TourAPI 응답을 파싱할 수 없습니다: " + raw, e);
        }
    }
}
