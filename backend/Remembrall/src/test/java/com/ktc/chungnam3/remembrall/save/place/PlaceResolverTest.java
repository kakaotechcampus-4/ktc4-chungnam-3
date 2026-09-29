package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceResolverTest {

    // 기본값: 반경 내 상가업소 데이터가 전혀 없는 경우(교통시설·공공기관 등)를 흉내낸다 -
    // hasNearbyStoreMismatch가 false를 반환해야 하는 케이스.
    private final PlaceResolver resolver = new PlaceResolver((lon, lat, radiusMeters) -> List.of());

    private static PlaceResolver resolverWithNearbyStores(
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver((lon, lat, radiusMeters) -> nearbyStores);
    }

    private static DataportalStoreClient.StoreResult store(String bizesNm) {
        return new DataportalStoreClient.StoreResult(bizesNm, "", "음식", "어딘가", 0, 0);
    }

    private static PlaceSearchClient.PlaceSearchResult result(String displayName, double lat, double lon) {
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, null, null);
    }

    private static PlaceSearchClient.PlaceSearchResult result(
            String displayName, double lat, double lon, String osmClass, String osmType) {
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, osmClass, osmType);
    }

    @Test
    void 검색결과가_없으면_NO_PLACE() {
        PlaceResolver.Result result = resolver.resolve("p1", "성심당", "본점", null, List.of());

        assertThat(result.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
        assertThat(result.resolvedPlace()).isNull();
        assertThat(result.confirmRequest()).isNull();
    }

    @Test
    void 검색결과가_한건이고_지점명이_없으면_이름만_맞아도_RESOLVED() {
        var match = result("대전 성심당 본점", 36.32, 127.42);

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", null, null, List.of(match));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().candidateId()).isEqualTo("p1");
        assertThat(resolved.resolvedPlace().lat()).isEqualTo(36.32);
        assertThat(resolved.confirmRequest()).isNull();
    }

    @Test
    void 검색결과가_한건이고_지점명도_일치하면_RESOLVED() {
        var match = result("대전 성심당 본점", 36.32, 127.42);

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", "본점", null, List.of(match));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.confirmRequest()).isNull();
    }

    @Test
    void 이름은_같지만_지점이_다르면_확정하지_않고_되묻는다() {
        // 검색어엔 지점명을 안 넣으므로("성심당 대전"), 결과가 1건이어도 그게 찾던 지점인지는
        // 별도로 확인해야 한다 (도구 검토 문서 2026-09-23, 개선 ①).
        var match = result("성심당 DCC점, 대전", 36.35, 127.38);

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", "본점", null, List.of(match));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.resolvedPlace()).isNull();
        assertThat(resolved.confirmRequest().candidates()).hasSize(1);
    }

    @Test
    void 검색결과가_2에서_3건이면_NEEDS_CONFIRMATION() {
        var matches = List.of(
                result("대전 성심당 본점", 36.32, 127.42),
                result("대전 성심당 DCC점", 36.35, 127.38)
        );

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", null, null, matches);

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.resolvedPlace()).isNull();
        assertThat(resolved.confirmRequest().candidates()).hasSize(2);
        assertThat(resolved.confirmRequest().candidates().get(0).candidateId()).isEqualTo("p1-1");
        assertThat(resolved.confirmRequest().candidates().get(1).candidateId()).isEqualTo("p1-2");
    }

    @Test
    void 검색결과가_4건_이상이면_NO_PLACE() {
        var matches = List.of(
                result("성심당 1호점", 1, 1),
                result("성심당 2호점", 2, 2),
                result("성심당 3호점", 3, 3),
                result("성심당 4호점", 4, 4)
        );

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", null, null, matches);

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 이름이_다른_결과는_전부_버려져_NO_PLACE() {
        // 실제 LocationIQ 테스트에서 관찰된 사례: "성심당 대전 본점" 검색 시 결과 둘 다
        // "성심당"이 아니라 전혀 무관한 업체였다 (하나는 주소도 대전이 아니라 천안).
        // 예전엔 지역 교차검증만 하고 이름은 안 봐서 「대전유니온약품 본점」이 그대로 확정되는
        // 버그가 있었다 (2026-09-23 코드 리뷰에서 지적받음) - 이제 이름부터 걸러서 NO_PLACE가 맞다.
        var wrongProvince = result(
                "대전충남양돈농협 본점, 50, 차돌로, Cheonan-Si, Seobuk, Chungcheongnam-Do", 36.80, 127.13);
        var wrongBusiness = result(
                "대전유니온약품 본점, 15, 혜천로, Daejon, Seo, Daejon", 36.30, 127.36);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(wrongProvince, wrongBusiness));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 이름은_같지만_지역이_다르면_교차검증으로_걸러져_NO_PLACE() {
        var onlyWrongProvince = result("성심당 부산점, Busan", 35.1, 129.0);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(onlyWrongProvince));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 매핑표에_없는_지역명이면_교차검증을_건너뛴다() {
        // "을지로" 같은 구/동 단위는 REGION_ALIASES에 없어서 필터링 없이 그대로 개수 판정으로 넘어간다.
        // (구/동 단위 오탐은 이 필터로 못 잡는 알려진 한계 - CLAUDE.md 참고)
        var matches = List.of(
                result("을지로 골뱅이, Jung-gu, Seoul", 37.566, 126.991),
                result("을지로 골뱅이, Jamsil-dong, Seoul", 37.508, 127.106)
        );

        PlaceResolver.Result resolved = resolver.resolve("p1", "을지로 골뱅이", null, "을지로", matches);

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }

    @Test
    void 반경내_상가업소_이름이_하나도_안맞으면_확정하지_않고_되묻는다() {
        // 「을지로 골뱅이 골목」이 시·도 단위 교차검증을 통과한 채 실제로는 잠실 좌표로 잘못
        // 확정되는 사례(CLAUDE.md 참고)를 흉내낸다 - 결과는 1건, 지역/이름 필터는 통과하지만
        // 반경 내 실제 상가업소 중엔 일치하는 이름이 없는 상황.
        var wrongCoordinate = result("을지로 골뱅이 골목", 37.508, 127.106);
        var resolver = resolverWithNearbyStores(List.of(store("편의점"), store("치킨집")));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "을지로 골뱅이 골목", null, null, List.of(wrongCoordinate));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.resolvedPlace()).isNull();
    }

    @Test
    void 반경내_상가업소_이름이_일치하면_RESOLVED_유지() {
        var correctCoordinate = result("을지로 골뱅이 골목", 37.566, 126.991);
        var resolver = resolverWithNearbyStores(List.of(store("을지로 골뱅이 골목"), store("편의점")));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "을지로 골뱅이 골목", null, null, List.of(correctCoordinate));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
    }

    @Test
    void 도로나_동네급_결과는_class로_걸러진다() {
        var street = result("성심당로, 대전", 36.30, 127.40, "highway", "residential");
        var neighbourhood = result("성심당 근처 동네, 대전", 36.31, 127.41, "place", "neighbourhood");
        var realShop = result("성심당 본점, 대전", 36.32, 127.42, "shop", "bakery");

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", null, null, List.of(street, neighbourhood, realShop));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("성심당 본점, 대전");
    }
}
