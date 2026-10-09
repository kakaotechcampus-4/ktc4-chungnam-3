package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceResolverTest {

    // 기본값: 반경 내 상가업소 데이터가 전혀 없는 경우(교통시설·공공기관 등)를 흉내낸다 -
    // hasNearbyStoreMismatch가 false를 반환해야 하는 케이스. PlaceLookup/FranchiseAnchorLookup은
    // 기존 테스트 전부 branchName이 없거나 regionHint가 시·도뿐이라 기준점 경로 자체가 안 타므로
    // 빈 값으로 충분하다.
    private final PlaceResolver resolver =
            new PlaceResolver((lon, lat, radiusMeters) -> List.of(), (name, branch, region) -> List.of(),
                    query -> Optional.empty(), 1000);

    private static PlaceResolver resolverWithNearbyStores(
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver((lon, lat, radiusMeters) -> nearbyStores, (name, branch, region) -> List.of(),
                query -> Optional.empty(), 1000);
    }

    private static PlaceResolver resolverForAnchor(
            List<PlaceSearchClient.PlaceSearchResult> anchorCandidates,
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> nearbyStores,
                (name, branch, region) -> anchorCandidates,
                query -> Optional.empty(),
                1000);
    }

    private static PlaceResolver resolverForVWorldAnchor(
            PlaceSearchClient.PlaceSearchResult vworldAnchor,
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> nearbyStores,
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                1000);
    }

    private static DataportalStoreClient.StoreResult store(String bizesNm) {
        return new DataportalStoreClient.StoreResult(bizesNm, "", "음식", "어딘가", 0, 0);
    }

    private static DataportalStoreClient.StoreResult store(
            String bizesNm, String brchNm, String roadAddress, double lat, double lon) {
        return new DataportalStoreClient.StoreResult(bizesNm, brchNm, "음식", roadAddress, lat, lon);
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

    @Test
    void 지하철역_버스정류장_태그는_class_필터에서_살아남는다() {
        // 실측 사례: "강남역"·"역삼역"·"선릉역"은 LocationIQ 후보가 전부 class=highway/type=bus_stop
        // 뿐이라, 기존처럼 class=highway를 통째로 걸렀다면 후보가 0건이 되어 NO_PLACE였을 상황.
        var busStop = result("강남역, 강남대로, 역삼1동, 서울특별시", 37.498, 127.028, "highway", "bus_stop");

        PlaceResolver.Result resolved = resolver.resolve("p1", "강남역", null, null, List.of(busStop));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
    }

    @Test
    void 버스정류장이_아닌_다른_도로_타입은_여전히_걸러진다() {
        // bus_stop만 예외지 실제 도로 구간(주거도로 등)은 그대로 걸러져야 한다.
        var residentialStreet = result("강남역로, 서울특별시", 37.5, 127.0, "highway", "residential");

        PlaceResolver.Result resolved = resolver.resolve("p1", "강남역", null, null, List.of(residentialStreet));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 지점명과_세부_지역힌트가_있으면_기준점_공공데이터로_확정한다() {
        // "스타벅스 강남역점" - LocationIQ 후보(searchResults)엔 진짜 강남역 지점이 아예 없는 상황을
        // 흉내낸다. 기준점("강남") 검색으로 강남역 좌표를 찾고, 그 주변 공공데이터에서 상호명이
        // 일치하는 업소를 찾아 확정해야 한다 - 원래 흐름(searchResults)은 아예 안 씀.
        var anchor = result("강남역, 서울특별시", 37.498, 127.028);
        var branchStore = store("스타벅스", "강남역점", "서울 강남구 어딘가", 37.498, 127.028);
        var resolver = resolverForAnchor(List.of(anchor), List.of(branchStore));

        var uselessSearchResults = List.of(
                result("스타벅스, 은평구, 서울특별시", 37.65, 126.93));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남", uselessSearchResults);

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울 강남구 어딘가");
    }

    @Test
    void 기준점_주변에_상호명이_일치하는_업소가_없으면_기존_흐름으로_폴백한다() {
        var anchor = result("홍대입구역, 서울특별시", 37.557, 126.924);
        var resolver = resolverForAnchor(List.of(anchor), List.of(store("전혀다른가게")));

        var fallbackSearchResults = List.of(result("이디야커피, 서울특별시", 37.56, 126.92));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "이디야커피", "홍대점", "서울 홍대", fallbackSearchResults);

        // 기준점 경로가 못 찾았으니(폴백) 기존 ①~④ 흐름대로 판정된다 - 1건, 지점명 불일치라 되묻기.
        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }

    @Test
    void regionHint가_시도뿐이면_기준점_경로를_시도하지_않는다() {
        // "대전"만으로는 기준점을 특정할 수 없으니 기준점 경로 자체를 건너뛰고 기존 흐름으로 간다.
        var resolver = resolverForAnchor(List.of(result("아무거나", 0, 0)), List.of(store("아무거나")));

        var searchResults = List.of(result("성심당, 대전광역시", 36.33, 127.43));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", searchResults);

        // 기준점 경로를 탔다면 "아무거나"가 엉뚱하게 확정됐을 것 - 그 대신 기존 흐름대로
        // 지점명("본점") 불일치로 되묻기가 나와야 한다.
        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }

    @Test
    void 지점명으로_한번더_좁혀_같은_브랜드_여러곳_중_정확한_지점을_고른다() {
        var anchor = result("종로, 서울특별시", 37.57, 126.99);
        var wrongBranch = store("교보문고", "잠실점", "서울 송파구 어딘가", 37.51, 127.10);
        var rightBranch = store("교보문고", "광화문점", "서울 종로구 어딘가", 37.571, 126.978);
        var resolver = resolverForAnchor(List.of(anchor), List.of(wrongBranch, rightBranch));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "교보문고", "광화문점", "서울 종로", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울 종로구 어딘가");
    }

    @Test
    void brchNm이_비어있어도_상호명에_지점명이_붙어있으면_확정한다() {
        // 공공 상가정보 실측 사례: brchNm 칸은 비어있고 bizesNm에 "스타벅스강남역점"처럼 지점명이
        // 그대로 붙어서 등록된 경우가 흔하다.
        var anchor = result("강남역, 서울특별시", 37.498, 127.028);
        var mergedNameStore = store("스타벅스강남역점", "", "서울 강남구 어딘가", 37.498, 127.028);
        var resolver = resolverForAnchor(List.of(anchor), List.of(mergedNameStore));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울 강남구 어딘가");
    }

    @Test
    void 지점명_텍스트가_전혀_달라도_기준점에서_확실히_가까우면_거리로_확정한다() {
        // 실측 사례: "강남역점"을 찾는데 실제 등록명은 "7번출구"뿐이라 문자열로는 전혀 안 걸림.
        // 그래도 기준점(강남역)에서 훨씬 더 가까운 지점이 하나 있으면 그곳으로 확정해야 한다.
        var anchor = result("강남역, 서울특별시", 37.4979, 127.0276);
        var closeBranch = store("스타벅스", "7번출구", "서초구 강남대로 385", 37.4980, 127.0277); // 몇 m 거리
        var farBranch = store("스타벅스", "", "서울 다른 동네 어딘가", 37.65, 126.93); // 수 km 거리
        var resolver = resolverForAnchor(List.of(anchor), List.of(farBranch, closeBranch));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서초구 강남대로 385");
    }

    @Test
    void 같은_장소가_가까운_좌표로_중복_등록되면_합쳐서_RESOLVED() {
        // 실측 사례("롯데월드"): 같은 건물이 shop/tourism 두 태그로 각각 등록돼 약 44m 떨어진
        // 좌표 2건이 나온다. 중복 등록 병합이 없으면 그대로 되묻기(2건)가 됐을 상황.
        var shopTag = result("롯데월드, 240, 올림픽로, 잠실3동, 서울특별시", 37.5114987, 127.0982387);
        var tourismTag = result("롯데월드, 240, 올림픽로, 잠실3동, 서울특별시", 37.5111041, 127.0982333);

        PlaceResolver.Result resolved = resolver.resolve("p1", "롯데월드", null, null, List.of(shopTag, tourismTag));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
    }

    @Test
    void 멀리_떨어진_서로_다른_실제_장소는_합치지_않고_되묻는다() {
        // 실측 사례("경복궁"): 궁궐 자체와 경복궁역이 같은 이름으로 잡히지만 약 516m 떨어진 별개
        // 장소다 - 병합 문턱(100m)보다 훨씬 멀어서 그대로 되묻기(2건)여야 한다.
        var palace = result("경복궁, 청운효자동, 서울특별시", 37.579754, 126.9766818);
        var subwayStation = result("경복궁, 130, 사직로, 사직동, 서울특별시", 37.5757924, 126.973629);

        PlaceResolver.Result resolved = resolver.resolve("p1", "경복궁", null, null, List.of(palace, subwayStation));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).hasSize(2);
    }

    @Test
    void 여러_출입구가_사슬처럼_이어져도_전이적으로_전부_하나로_합쳐진다() {
        // A-B, B-C는 각각 100m 문턱 안이지만 A-C는 약 180m로 문턱 밖이다 - 그래도 B를 거쳐
        // 전이적으로 한 그룹이어야 한다("서울역"처럼 출입구가 여럿인 큰 역을 흉내낸 사례).
        var exitA = result("서울역, 지하392, 서울특별시", 37.5000000, 127.0000000);
        var exitB = result("서울역, 세종대로, 서울특별시", 37.5008083, 127.0000000); // A에서 약 90m
        var exitC = result("서울역, 청파로, 서울특별시", 37.5016166, 127.0000000); // B에서 약 90m, A에서 약 180m

        PlaceResolver.Result resolved = resolver.resolve("p1", "서울역", null, null, List.of(exitA, exitB, exitC));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
    }

    @Test
    void 거리가_비슷하게_가까운_후보가_여럿이면_되묻는다() {
        // 같은 건물/구역에 같은 브랜드 지점이 여러 개 있어서 거리로도 확신할 수 없는 경우.
        var anchor = result("강남역, 서울특별시", 37.4979, 127.0276);
        var branch1 = store("스타벅스", "", "서초구 강남대로 385", 37.4980, 127.0277);
        var branch2 = store("스타벅스", "", "서초구 강남대로 389", 37.4981, 127.0278); // 비슷하게 가까움
        var resolver = resolverForAnchor(List.of(anchor), List.of(branch1, branch2));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.resolvedPlace()).isNull();
    }

    @Test
    void VWorld_기준점으로_LocationIQ가_못찾던_매장도_지점명으로_좁혀_되묻는다() {
        // 실측 사례("이디야커피 홍대청기와점"): LocationIQ는 지오코딩 자체를 못 하고, 공공 상가정보
        // 등록명("이디야홍대청기와점")엔 "커피"가 없어 브랜드명 문자열 비교도 실패하던 매장(2026-10-09
        // 실제 API로 재확인 - 등록명은 지금도 "이디야홍대청기와점"이 맞음, 커피 없음).
        // 예전엔 이 경우 브랜드 무관 전체를 거리로 확정했었는데(PR 리뷰로 발견된 버그 - 엉뚱한 가게가
        // 확정될 위험), 이제는 지점명("홍대청기와점")이 상호명에 그대로 들어있는지로 한 번 더 좁혀서
        // 찾되, 브랜드가 확인 안 된 거라 자동 확정은 안 하고 되묻는다.
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.5556, 126.9207, null, null);
        var store = store("이디야홍대청기와점", "", "서울 마포구 월드컵북로 7", 37.5556, 126.9207);
        var resolver = resolverForVWorldAnchor(vworldAnchor, List.of(store));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "이디야커피", "홍대청기와점", "서울 홍대", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).extracting("address")
                .containsExactly("서울 마포구 월드컵북로 7");
    }

    @Test
    void VWorld_기준점이_regionHint와_다른_지역이면_그_반경_업소로_확정하지_않는다() {
        // PR 리뷰(doheelab-coder)로 발견된 버그 재현 - "CU 중앙점"처럼 흔한 지점명을 VWorld가 regionHint
        // (대전)와 무관한 다른 지역(부산)에서 찾아줘도, 그 반경 안 공공상가정보 업소를 그대로 확정해버리면
        // 안 된다. 기준점 좌표 자체는 임의값(테스트에서 중요한 건 반경 안 업소의 roadAddress).
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 35.1796, 129.0756, null, null);
        var wrongRegionStore = store("CU", "중앙점", "부산광역시 중구 중앙동", 35.1796, 129.0756);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(wrongRegionStore),
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                1000);

        var fallbackSearchResults = List.of(result("CU, 대전광역시", 36.35, 127.38));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "CU", "중앙점", "대전 중구", fallbackSearchResults);

        // VWorld 기준점은 찾았지만 그 반경 안 업소가 전부 엉뚱한 지역(부산)이라 걸러져서, 기존 ①~④
        // 흐름으로 폴백한다 - 부산 업소를 대전 지점으로 잘못 확정하면 안 된다.
        assertThat(resolved.decision()).isNotEqualTo(PlaceResolver.Decision.RESOLVED);
    }

    @Test
    void CU처럼_짧은_브랜드명은_업종_소분류로_먼저_좁혀서_과매칭을_피한다() {
        // 실측 사례(2026-10-08): "CU"로 대분류(G2)만 걸고 상호명 부분일치로 찾으면 반경 안 무관한
        // 업소까지 15건 걸려 NO_PLACE가 됐었다. 업종 소분류(G20405=편의점)로 먼저 좁히면 편의점끼리만
        // 남는다. 공공상가정보엔 "CU"가 로마자가 아니라 한글 음차("씨유")로 등록돼 있어서(실측 확인),
        // BRAND_NAME_ALIASES로 브랜드명 매칭도 같이 통과해야 한다.
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.4979, 127.0276, null, null);
        var cuStore = store("씨유서초삼성타운점", "", "서울특별시 서초구 서초대로74길 23", 37.4956258989903, 127.027094692608);
        NearbyStoreLookup lookup = new NearbyStoreLookup() {
            @Override
            public List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
                return List.of();
            }

            @Override
            public List<DataportalStoreClient.StoreResult> searchByRadius(
                    double lon, double lat, int radiusMeters, List<String> industryLargeCategoryCodes) {
                // 대분류(I2/G2)로 걸면 과매칭 재현 - 무관한 업소 15건 + CU 자체는 섞여있지 않다고 가정
                // (실제로는 CU도 섞여서 나오지만, 여기선 "소분류를 안 쓰면 못 찾는다"를 분명히 하기 위해
                // 일부러 뺐다 - 소분류 경로를 안 타면 이 테스트가 NO_PLACE로 실패해야 정상).
                return IntStream.range(0, 15)
                        .mapToObj(i -> store("무관한업소" + i, "", "서울 어딘가", 37.5, 127.0))
                        .toList();
            }

            @Override
            public List<DataportalStoreClient.StoreResult> searchByRadiusBySubCategory(
                    double lon, double lat, int radiusMeters, List<String> industrySubCategoryCodes) {
                assertThat(industrySubCategoryCodes).containsExactly("G20405");
                return List.of(cuStore);
            }
        };
        var resolver = new PlaceResolver(lookup, (name, branch, region) -> List.of(), query -> Optional.of(vworldAnchor), 1000);

        PlaceResolver.Result resolved = resolver.resolve("p1", "CU", "서초삼성타운점", "서울 서초", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울특별시 서초구 서초대로74길 23");
    }

    @Test
    void VWorld_기준점_주변에도_공공데이터가_없으면_기존_흐름으로_폴백한다() {
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.5556, 126.9207, null, null);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(),
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                1000);

        var fallbackSearchResults = List.of(result("이디야커피, 서울특별시", 37.56, 126.92));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "이디야커피", "홍대청기와점", "서울 홍대", fallbackSearchResults);

        // VWorld 기준점은 찾았지만 그 주변 공공데이터가 비어있으니(폴백) 기존 ①~④ 흐름대로
        // 판정된다 - 1건, 지점명 불일치라 되묻기.
        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }

    @Test
    void VWorld_기준점_반경_안에_브랜드도_지점명도_안_맞으면_확정하지_않고_폴백한다() {
        // PR 리뷰(doheelab-coder)로 발견된 버그 재현 - "CU 중구청점"을 찾았는데 반경 안엔 지역은 맞지만
        // (대전) 브랜드도 지점명도 전혀 다른 가게만 있는 경우, 예전엔 "상호명 표기 불일치일 수 있다"며
        // 그 가게를 그대로 CU로 확정해버렸다. 이제는 이 기준점 자체를 못 믿겠다고 보고 VWorld 경로를
        // 포기해서, 엉뚱한 가게로 확정되는 일 자체가 없어야 한다(폴백한 기존 흐름에서 최종 판정).
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 36.35, 127.38, null, null);
        var unrelatedStore = store("세정 중정점", "", "대전광역시 중구 중앙로 100", 36.35, 127.38);
        var resolver = resolverForVWorldAnchor(vworldAnchor, List.of(unrelatedStore));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "CU", "중구청점", "대전 중구", List.of());

        // VWorld 경로가 포기하고 기존 ①~④ 흐름으로 폴백하는데, 전달받은 LocationIQ 검색 결과 자체가
        // 없으니(테스트에서 빈 리스트) 최종적으로 NO_PLACE가 된다 - 핵심은 "세정 중정점"이 CU로
        // 잘못 확정되지 않는다는 것.
        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void VWorld_기준점_반경_안에_지점명_일치_후보가_상한을_넘으면_확정하지_않고_폴백한다() {
        // 브랜드는 안 맞는데 지점명 텍스트는 우연히 여럿에 걸리는 경우(상한 초과) - 되묻기도 의미
        // 없으니 이 기준점 자체를 포기하고 기존 ①~④ 흐름으로 폴백한다(개수 상한 초과 시 바로 NO_PLACE로
        // 끊어버리는 게 아니라, narrowByBrandThenDistance의 1차 텍스트매칭 상한 초과 처리와는 다르게
        // "폴백"을 택한 것 - resolveViaVWorldAnchor는 브랜드 확인이 아예 안 된 상태라 더 보수적으로 감).
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 36.35, 127.38, null, null);
        var unrelatedStores = IntStream.range(0, 4)
                .mapToObj(i -> store("무관한업소" + i + "중구청점", "", "대전광역시 중구 중앙로 " + i, 36.35, 127.38))
                .toList();
        var resolver = resolverForVWorldAnchor(vworldAnchor, unrelatedStores);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "CU", "중구청점", "대전 중구", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NO_PLACE);
    }

    @Test
    void 지점명있는_다건후보도_공공데이터_교차검증으로_하나로_좁혀지면_확정한다() {
        // 실측 사례("성심당 대전 본점"): LocationIQ가 대전 내 성심당 지점 3곳(본점·유성구점·대전역점)을
        // 전부 돌려준다 - 기존엔 후보가 1건일 때만 지점명을 확인해서 이런 경우 그대로 되묻기였다.
        var honjeom = result("성심당, 대종로480번길, 중구, 대전광역시", 36.3277, 127.4273);
        var yuseong = result("성심당, 엑스포로123번길, 유성구, 대전광역시", 36.3753, 127.3922);
        var daejeonStation = result("성심당, 대전역지하차도, 동구, 대전광역시", 36.3324, 127.4338);

        var honjeomStore = store("성심당", "본점", "대전 중구 대종로480번길 15", 36.3277, 127.4273);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> lat == 36.3277 ? List.of(honjeomStore) : List.of(),
                (name, branch, region) -> List.of(),
                query -> Optional.empty(),
                1000);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(honjeom, yuseong, daejeonStation));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("성심당, 대종로480번길, 중구, 대전광역시");
    }

    @Test
    void 다건후보중_둘이상에서_지점명이_일치하면_확신할수없어_그대로_되묻는다() {
        var branchA = result("성심당, A, 대전광역시", 36.32, 127.42);
        var branchB = result("성심당, B, 대전광역시", 36.35, 127.39);
        var storeA = store("성심당", "본점", "대전 어딘가 A", 36.32, 127.42);
        var storeB = store("성심당", "본점", "대전 어딘가 B", 36.35, 127.39);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> lat == 36.32 ? List.of(storeA) : List.of(storeB),
                (name, branch, region) -> List.of(),
                query -> Optional.empty(),
                1000);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(branchA, branchB));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).hasSize(2);
    }
}
