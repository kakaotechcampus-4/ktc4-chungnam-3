package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSearchClientTest {

    private final PlaceSearchClient client = new PlaceSearchClient("test-key", 2000, 3000);

    @Test
    void name에_지역명이_없으면_regionHint의_시도명을_덧붙인다() {
        String query = client.buildQuery("성심당", "대전");

        assertThat(query).isEqualTo("성심당 대전");
    }

    @Test
    void name에_이미_지역명이_있으면_중복으로_덧붙이지_않는다() {
        // 실측 확인(2026-10-02): "서울 마포구 월드컵북로 7 서울"처럼 지역명이 중복되면 LocationIQ가
        // "Unable to geocode"로 완전히 실패한다 - 주소 문자열을 통째로 넣는 경로에서 특히 흔한 버그였다.
        String query = client.buildQuery("서울 마포구 월드컵북로 7", "서울");

        assertThat(query).isEqualTo("서울 마포구 월드컵북로 7");
    }

    @Test
    void regionHint가_없으면_name만_그대로_쓴다() {
        String query = client.buildQuery("성심당", null);

        assertThat(query).isEqualTo("성심당");
    }
}
