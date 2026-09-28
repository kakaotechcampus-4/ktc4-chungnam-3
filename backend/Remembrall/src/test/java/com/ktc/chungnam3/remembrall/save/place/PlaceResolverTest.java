package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceResolverTest {

    private final PlaceResolver resolver = new PlaceResolver();

    @Test
    void 검색결과가_없으면_NO_PLACE() {
        PlaceResolver.Result result = resolver.resolve("p1", "성심당", "본점", null, List.of());

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
        assertThat(result.resolvedPlace()).isNull();
        assertThat(result.confirmRequest()).isNull();
    }

    @Test
    void 검색결과가_한건이면_RESOLVED() {
        var match = new PlaceSearchClient.PlaceSearchResult("대전 성심당 본점", 36.32, 127.42);

        PlaceResolver.Result result = resolver.resolve("p1", "성심당", "본점", null, List.of(match));

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(result.resolvedPlace().candidateId()).isEqualTo("p1");
        assertThat(result.resolvedPlace().lat()).isEqualTo(36.32);
        assertThat(result.confirmRequest()).isNull();
    }

    @Test
    void 검색결과가_2에서_3건이면_NEEDS_CONFIRMATION() {
        var matches = List.of(
                new PlaceSearchClient.PlaceSearchResult("대전 성심당 본점", 36.32, 127.42),
                new PlaceSearchClient.PlaceSearchResult("대전 성심당 DCC점", 36.35, 127.38)
        );

        PlaceResolver.Result result = resolver.resolve("p1", "성심당", null, null, matches);

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(result.resolvedPlace()).isNull();
        assertThat(result.confirmRequest().candidates()).hasSize(2);
        assertThat(result.confirmRequest().candidates().get(0).candidateId()).isEqualTo("p1-1");
        assertThat(result.confirmRequest().candidates().get(1).candidateId()).isEqualTo("p1-2");
    }

    @Test
    void 검색결과가_4건_이상이면_NO_PLACE() {
        var matches = List.of(
                new PlaceSearchClient.PlaceSearchResult("a", 1, 1),
                new PlaceSearchClient.PlaceSearchResult("b", 2, 2),
                new PlaceSearchClient.PlaceSearchResult("c", 3, 3),
                new PlaceSearchClient.PlaceSearchResult("d", 4, 4)
        );

        PlaceResolver.Result result = resolver.resolve("p1", "성심당", null, null, matches);

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 다른_시도_결과는_지역_교차검증으로_걸러진다() {
        // 실제 LocationIQ 테스트에서 관찰된 사례: "성심당 대전 본점" 검색 시 결과 하나는
        // 주소가 대전이 아니라 천안(Cheonan-Si)이었다.
        var wrongProvince = new PlaceSearchClient.PlaceSearchResult(
                "대전충남양돈농협 본점, 50, 차돌로, Cheonan-Si, Seobuk, Chungcheongnam-Do", 36.80, 127.13);
        var rightProvince = new PlaceSearchClient.PlaceSearchResult(
                "대전유니온약품 본점, 15, 혜천로, Daejon, Seo, Daejon", 36.30, 127.36);

        PlaceResolver.Result result = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(wrongProvince, rightProvince));

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(result.resolvedPlace().address()).contains("Daejon");
    }

    @Test
    void 교차검증으로_전부_걸러지면_NO_PLACE() {
        var onlyWrongProvince = new PlaceSearchClient.PlaceSearchResult(
                "부산 어딘가, Busan", 35.1, 129.0);

        PlaceResolver.Result result = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(onlyWrongProvince));

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 매핑표에_없는_지역명이면_교차검증을_건너뛴다() {
        // "을지로" 같은 구/동 단위는 REGION_ALIASES에 없어서 필터링 없이 그대로 개수 판정으로 넘어간다.
        // (구/동 단위 오탐은 이 필터로 못 잡는 알려진 한계 - CLAUDE.md 참고)
        var matches = List.of(
                new PlaceSearchClient.PlaceSearchResult("을지로 골뱅이, Jung-gu, Seoul", 37.566, 126.991),
                new PlaceSearchClient.PlaceSearchResult("을지로 골뱅이, Jamsil-dong, Seoul", 37.508, 127.106)
        );

        PlaceResolver.Result result = resolver.resolve("p1", "을지로 골뱅이", null, "을지로", matches);

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }
}
