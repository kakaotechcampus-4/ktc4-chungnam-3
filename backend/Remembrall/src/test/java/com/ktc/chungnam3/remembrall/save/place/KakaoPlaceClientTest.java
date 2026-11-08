package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoPlaceClientTest {

    @Test
    void 카카오_장소명_띄어쓰기가_달라도_같은_이름으로_본다() {
        // 실서버 재현(2026-11-08): 되묻기 후보 "오일내둔산"(공공상가정보) vs 카카오 "오일내 둔산시청점" -
        // 위치는 정확했는데 공백 때문에 매번 404가 났다.
        assertThat(KakaoPlaceClient.nameMatches("오일내 둔산시청점", "오일내둔산")).isTrue();
        assertThat(KakaoPlaceClient.nameMatches("2002횟집", "2002 횟집")).isTrue();
    }

    @Test
    void 기존처럼_브랜드가_포함되면_맞고_대소문자는_무시한다() {
        assertThat(KakaoPlaceClient.nameMatches("이디야커피 홍대청기와점", "이디야커피")).isTrue();
        assertThat(KakaoPlaceClient.nameMatches("CU 서초삼성타운점", "cu")).isTrue();
    }

    @Test
    void 다른_가게나_이름이_없는_결과는_맞지_않는다() {
        assertThat(KakaoPlaceClient.nameMatches("오일내 둔산시청점", "2002횟집")).isFalse();
        assertThat(KakaoPlaceClient.nameMatches(null, "오일내둔산")).isFalse();
    }
}
