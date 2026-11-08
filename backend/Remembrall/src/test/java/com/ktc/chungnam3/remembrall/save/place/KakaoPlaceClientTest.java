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
    void 원래_글자_그대로_들어있으면_단어_중간이어도_맞는다() {
        assertThat(KakaoPlaceClient.nameMatches("원조할매순대국", "할매순대국")).isTrue();
    }

    @Test
    void 단어_중간에_그대로_들어있으면_영문_대소문자는_무시한다() {
        assertThat(KakaoPlaceClient.nameMatches("카페Starbucks", "starbucks")).isTrue();
    }

    @Test
    void 공백을_지워야만_맞는데_단어_중간에서_시작하면_다른_가게로_본다() {
        // PR 리뷰(doheelab-coder): 공백만 지우면 "성공 차돌박이"가 "공차"로, "MAGIC UNIVERSE"가 "CU"로 맞았다 -
        // 엉뚱한 가게에 카카오 ID가 붙으면 Place가 카카오 ID마다 하나라 두 가게가 한 Place로 합쳐진다.
        assertThat(KakaoPlaceClient.nameMatches("성공 차돌박이", "공차")).isFalse();
        assertThat(KakaoPlaceClient.nameMatches("MAGIC UNIVERSE", "CU")).isFalse();
    }

    @Test
    void 공백을_지워야_맞아도_뒤쪽_단어의_첫머리에서_시작하면_맞는다() {
        assertThat(KakaoPlaceClient.nameMatches("원조 할매순대국", "할매 순대국")).isTrue();
    }

    @Test
    void 다른_가게나_이름이_없는_결과는_맞지_않는다() {
        assertThat(KakaoPlaceClient.nameMatches("오일내 둔산시청점", "2002횟집")).isFalse();
        assertThat(KakaoPlaceClient.nameMatches(null, "오일내둔산")).isFalse();
    }
}
