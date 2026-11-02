package com.ktc.chungnam3.remembrall.save.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아래 raw JSON은 실제 TourAPI 호출로 받은 응답 그대로다(TourApiRawFieldCheckScratchTest로 재현
 * 가능, 2026-10-28 실측) - 필드명을 추측하지 않고 실제 응답 구조(0건일 때 items가 객체가 아니라
 * 빈 문자열로 오는 data.go.kr 특유의 함정 포함)로 파싱 로직을 검증한다.
 */
class TourApiClientTest {

    private final TourApiClient client = new TourApiClient("test-key", 2000, 3000, new ObjectMapper());

    @Test
    void 완전일치_단건_응답을_파싱한다() {
        String raw = "{\"response\": {\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"},"
                + "\"body\": {\"items\": {\"item\":[{\"addr1\":\"강원특별자치도 원주시 문막읍 반저리2길 42\","
                + "\"contentid\":\"2613436\",\"contenttypeid\":\"12\",\"mapx\":\"127.7879451460\","
                + "\"mapy\":\"37.2938782671\",\"title\":\"원주 반계리 은행나무\"}]},"
                + "\"numOfRows\":1,\"pageNo\":1,\"totalCount\":1}}}";

        List<TourApiClient.TourApiItem> items = client.parseItems(raw);

        assertThat(items).hasSize(1);
        TourApiClient.TourApiItem item = items.get(0);
        assertThat(item.contentId()).isEqualTo("2613436");
        assertThat(item.title()).isEqualTo("원주 반계리 은행나무");
        assertThat(item.address()).isEqualTo("강원특별자치도 원주시 문막읍 반저리2길 42");
        // mapx=lon, mapy=lat - TourApiItem 필드 순서(lat, lon)와 반대라 뒤바뀌기 쉬운 지점.
        assertThat(item.lat()).isEqualTo(37.2938782671);
        assertThat(item.lon()).isEqualTo(127.7879451460);
    }

    @Test
    void 결과가_여러건이면_전부_파싱한다() {
        String raw = "{\"response\": {\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"},"
                + "\"body\": {\"items\": {\"item\":["
                + "{\"addr1\":\"경기도 수원시 권선구 효원로256번길 16\",\"contentid\":\"2739473\","
                + "\"contenttypeid\":\"12\",\"mapx\":\"127.0285784659\",\"mapy\":\"37.2614742906\","
                + "\"title\":\"수원올림픽공원\"},"
                + "{\"addr1\":\"서울특별시 송파구 올림픽로 424\",\"contentid\":\"126532\","
                + "\"contenttypeid\":\"12\",\"mapx\":\"127.11505157122546\",\"mapy\":\"37.520555375396896\","
                + "\"title\":\"올림픽공원\"}]},"
                + "\"numOfRows\":2,\"pageNo\":1,\"totalCount\":2}}}";

        List<TourApiClient.TourApiItem> items = client.parseItems(raw);

        assertThat(items).extracting(TourApiClient.TourApiItem::title)
                .containsExactly("수원올림픽공원", "올림픽공원");
    }

    @Test
    void 검색결과가_0건이면_items가_빈_문자열로_와도_빈_리스트를_반환한다() {
        // 실측 확인(2026-10-28): totalCount=0일 때 items가 객체가 아니라 ""(빈 문자열)로 온다 -
        // item을 레코드로 바로 매핑했다면 역직렬화 자체가 깨졌을 케이스.
        String raw = "{\"response\": {\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"},"
                + "\"body\": {\"items\": \"\",\"numOfRows\":0,\"pageNo\":1,\"totalCount\":0}}}";

        List<TourApiClient.TourApiItem> items = client.parseItems(raw);

        assertThat(items).isEmpty();
    }

    @Test
    void resultCode가_0000이_아니면_예외를_던진다() {
        String raw = "{\"response\": {\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}}}";

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> client.parseItems(raw)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }
}
