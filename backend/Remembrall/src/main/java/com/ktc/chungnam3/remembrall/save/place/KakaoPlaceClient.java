package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 카카오 로컬 API(키워드 장소검색)로, 파이프라인이 이미 확정한 좌표가 카카오 데이터베이스상 어떤
 * 장소 ID인지 찾는다 - Place.md 설계상 "같은 장소인지" 판단은 카카오 장소 ID(verificationPlaceId)
 * 기준이라, 확정된 장소마다 이 ID만 받아서 붙이는 용도다(PR 리뷰 제안, 2026-10-10).
 * <p>
 * <b>장소 ID 외에는 어떤 필드도 반환하지 않는다.</b> 카카오 응답의 이름·주소·좌표·카테고리는 저장은
 * 물론 반환조차 하지 않는다 - Place.md("카카오가 반환한 장소명, 주소, 좌표와 카테고리는 저장하지
 * 않습니다")와 같은 원칙을 {@link VWorldSearchClient}처럼 반환 타입 자체로 구조적으로 강제한다.
 * <p>
 * 1순위 결과를 그냥 믿지 않는다 - 호출부가 이미 확정한 좌표를 알고 있으므로, 그 좌표를 중심으로
 * 좁은 반경(x/y/radius/sort=distance)만 검색하고, 그중 상호명에 찾던 브랜드가 포함된 가장 가까운
 * 결과만 쓴다(2026-10-10 실측: "이디야커피 홍대청기와점"을 그 좌표로 검색하면 거리 3m로 정확히
 * 일치하는 결과 1건이 옴). 반경 안에 브랜드가 일치하는 곳이 없으면(카카오 미등록·동명이인 매장만
 * 있음) 빈 값을 반환한다.
 */
@Component
public class KakaoPlaceClient implements KakaoPlaceLookup {

    private static final String BASE_URL = "https://dapi.kakao.com";

    // 실측 확인(2026-10-10): "CU 서초삼성타운점"의 공공상가정보 등록 좌표와 카카오 자체 핀 좌표가
    // 같은 매장인데도 141m 차이가 났다(제공사마다 좌표 정밀도가 다름 - 흔한 일). 50m로는 이 케이스를
    // 놓쳐서 200m로 넓힘. 어차피 브랜드+지점명을 쿼리 텍스트에 그대로 넣어 카카오 자체 검색이 이미
    // 정확히 그 지점 하나로 좁혀주므로(실측: "CU 서초삼성타운점" 쿼리는 반경 안 CU 여러 곳 중 정확히
    // 1건만 반환함), 반경을 넓혀도 동명 지점이 섞여 들어올 위험은 낮다.
    private static final int RADIUS_METERS = 200;

    private final RestClient restClient;
    private final String apiKey;

    public KakaoPlaceClient(
            @Value("${kakao.api.key}") String apiKey,
            @Value("${kakao.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${kakao.read-timeout-ms:3000}") int readTimeoutMs) {
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
    private record ResponseWrapper(List<Document> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Document(String id, @JsonProperty("place_name") String placeName) {
    }

    /**
     * 브랜드+지점명과 이미 확정된 좌표로 카카오 장소 ID를 찾는다. 반경 안에 상호명이 브랜드를 포함하는
     * 결과가 없으면(카카오 미등록·동명이인 매장만 있음 등) 빈 값을 반환한다 - 호출부가 "ID 못 찾음"을
     * 정상 케이스로 처리할 수 있어야 한다(VWorld/LocationIQ 등 기존 클라이언트와 같은 원칙).
     */
    @Override
    public Optional<String> findPlaceId(String name, String branchName, double lat, double lon) {
        String query = branchName == null || branchName.isBlank() ? name : name + " " + branchName;

        ResponseWrapper response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("x", lon)
                            .queryParam("y", lat)
                            .queryParam("radius", RADIUS_METERS)
                            .queryParam("sort", "distance")
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(ResponseWrapper.class);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        if (response == null || response.documents() == null) {
            return Optional.empty();
        }

        return response.documents().stream()
                .filter(doc -> doc.placeName() != null && doc.placeName().contains(name))
                .findFirst()
                .map(Document::id);
    }
}
