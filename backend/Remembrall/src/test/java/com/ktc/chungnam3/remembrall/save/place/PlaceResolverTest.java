package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
                    query -> Optional.empty(), name -> List.of(), 1000);

    private static PlaceResolver resolverWithNearbyStores(
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver((lon, lat, radiusMeters) -> nearbyStores, (name, branch, region) -> List.of(),
                query -> Optional.empty(), name -> List.of(), 1000);
    }

    private static PlaceResolver resolverForAnchor(
            List<PlaceSearchClient.PlaceSearchResult> anchorCandidates,
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> nearbyStores,
                (name, branch, region) -> anchorCandidates,
                query -> Optional.empty(),
                name -> List.of(),
                1000);
    }

    private static PlaceResolver resolverForVWorldAnchor(
            PlaceSearchClient.PlaceSearchResult vworldAnchor,
            List<DataportalStoreClient.StoreResult> nearbyStores) {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> nearbyStores,
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                name -> List.of(),
                1000);
    }

    private static PlaceResolver resolverForTourApi(List<TourApiClient.TourApiItem> tourApiItems) {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(),
                (name, branch, region) -> List.of(),
                query -> Optional.empty(),
                name -> tourApiItems,
                1000);
    }

    private static DataportalStoreClient.StoreResult store(String bizesNm) {
        return new DataportalStoreClient.StoreResult("", bizesNm, "", "음식", "어딘가", 0, 0);
    }

    private static DataportalStoreClient.StoreResult store(
            String bizesNm, String brchNm, String roadAddress, double lat, double lon) {
        return new DataportalStoreClient.StoreResult("", bizesNm, brchNm, "음식", roadAddress, lat, lon);
    }

    private static PlaceSearchClient.PlaceSearchResult result(String displayName, double lat, double lon) {
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, null, null, "");
    }

    private static PlaceSearchClient.PlaceSearchResult result(
            String displayName, double lat, double lon, String osmClass, String osmType) {
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, osmClass, osmType, "");
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
    void 지점명_텍스트가_전혀_달라도_기준점에서_확실히_가까우면_거리로_좁혀_되묻는다() {
        // 실측 사례: "강남역점"을 찾는데 실제 등록명은 "7번출구"뿐이라 문자열로는 전혀 안 걸림.
        // 기준점(강남역)에서 훨씬 더 가까운 지점이 하나 있으면 그곳으로 좁히긴 하지만, 순전히 거리로만
        // 좁힌 거라 그게 찾던 그 지점이라고 확신할 순 없다(PR 리뷰로 발견된 버그 수정, 2026-10-09) -
        // 자동 확정 대신 confirmOnly로 되묻는다.
        var anchor = result("강남역, 서울특별시", 37.4979, 127.0276);
        var closeBranch = store("스타벅스", "7번출구", "서초구 강남대로 385", 37.4980, 127.0277); // 몇 m 거리
        var farBranch = store("스타벅스", "", "서울 다른 동네 어딘가", 37.65, 126.93); // 수 km 거리
        var resolver = resolverForAnchor(List.of(anchor), List.of(farBranch, closeBranch));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).extracting("address")
                .containsExactly("서초구 강남대로 385");
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
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.5556, 126.9207, null, null, null);
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
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 35.1796, 129.0756, null, null, null);
        var wrongRegionStore = store("CU", "중앙점", "부산광역시 중구 중앙동", 35.1796, 129.0756);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(wrongRegionStore),
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                name -> List.of(),
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
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.4979, 127.0276, null, null, null);
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
        var resolver = new PlaceResolver(lookup, (name, branch, region) -> List.of(), query -> Optional.of(vworldAnchor),
                name -> List.of(), 1000);

        PlaceResolver.Result resolved = resolver.resolve("p1", "CU", "서초삼성타운점", "서울 서초", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울특별시 서초구 서초대로74길 23");
    }

    @Test
    void VWorld_기준점_주변에도_공공데이터가_없으면_기존_흐름으로_폴백한다() {
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 37.5556, 126.9207, null, null, null);
        var resolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(),
                (name, branch, region) -> List.of(),
                query -> Optional.of(vworldAnchor),
                name -> List.of(),
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
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 36.35, 127.38, null, null, null);
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
        var vworldAnchor = new PlaceSearchClient.PlaceSearchResult(null, 36.35, 127.38, null, null, null);
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
                name -> List.of(),
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
                name -> List.of(),
                1000);

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "성심당", "본점", "대전", List.of(branchA, branchB));

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).hasSize(2);
    }

    @Test
    void LocationIQ로_확정하면_출처는_LOCATIONIQ이고_ID는_그_결과의_place_id다() {
        var match = new PlaceSearchClient.PlaceSearchResult("성심당 본점, 대전", 36.32, 127.42, null, null, "227225446");

        PlaceResolver.Result resolved = resolver.resolve("p1", "성심당", null, null, List.of(match));

        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.LOCATIONIQ);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("227225446");
    }

    @Test
    void 공공데이터로_확정하면_출처는_DATAPORTAL이고_ID는_bizesId다() {
        var anchor = result("강남역, 서울특별시", 37.498, 127.028);
        var branchStore = new DataportalStoreClient.StoreResult(
                "MA0101", "스타벅스", "강남역점", "음식", "서울 강남구 어딘가", 37.498, 127.028);
        var resolver = resolverForAnchor(List.of(anchor), List.of(branchStore));

        PlaceResolver.Result resolved = resolver.resolve(
                "p1", "스타벅스", "강남역점", "서울 강남",
                List.of(result("스타벅스, 은평구, 서울특별시", 37.65, 126.93)));

        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.DATAPORTAL);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA0101");
    }

    private static TourApiClient.TourApiItem tourApiItem(String title, String address) {
        return new TourApiClient.TourApiItem("1", title, address, 36.37, 127.39);
    }

    @Test
    void 지점명없는_일반장소는_TourAPI_제목_완전일치로_RESOLVED() {
        // 실측 사례("올림픽공원" - contentTypeId=12로 좁히면 서울 송파 올림픽공원만 제목이 정확히
        // 일치, 수원/창원의 "OO올림픽공원"은 완전일치에서 자동으로 빠진다, 2026-10-28).
        var resolver = resolverForTourApi(List.of(
                tourApiItem("올림픽공원", "서울특별시 송파구 올림픽로 424"),
                tourApiItem("수원올림픽공원", "경기도 수원시 권선구 효원로256번길 16")));

        PlaceResolver.Result resolved = resolver.resolve("p1", "올림픽공원", null, null, List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울특별시 송파구 올림픽로 424");
        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.TOURAPI);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("1");
    }

    @Test
    void 완전일치가_없으면_끝일치로_구제해_RESOLVED() {
        // 실측 사례("반계리 은행나무" - 공식 제목은 "원주 반계리 은행나무", 2026-10-28).
        var resolver = resolverForTourApi(List.of(tourApiItem("원주 반계리 은행나무", "강원특별자치도 원주시 문막읍 반저리2길 42")));

        PlaceResolver.Result resolved = resolver.resolve("p1", "반계리 은행나무", null, null, List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("강원특별자치도 원주시 문막읍 반저리2길 42");
    }

    @Test
    void 완전일치_끝일치_모두_없으면_기존_LocationIQ_흐름으로_폴백한다() {
        // 실측 사례("용문사" - 전국에 동명이인이 많아 전부 "용문사(남해)"처럼 접두어/괄호가 붙어있고
        // "용문사" 그 자체로 끝나는 제목이 하나도 없다, 2026-10-28). contains가 아니라 endsWith라서
        // 이 케이스는 구제되지 않고 그대로 기존 흐름(LocationIQ)으로 넘어가야 한다.
        var resolver = resolverForTourApi(List.of(
                tourApiItem("용문사(남해)", "경상남도 남해군 이동면 용문사길 166-11"),
                tourApiItem("양평 용문사 은행나무", "경기도 양평군 용문면 용문산로 782")));

        var fallback = List.of(result("용문사, 경기도", 37.55, 127.57));
        PlaceResolver.Result resolved = resolver.resolve("p1", "용문사", null, null, fallback);

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.LOCATIONIQ);
    }

    @Test
    void 끝일치가_여러건이어도_regionHint로_좁혀지면_RESOLVED() {
        var resolver = resolverForTourApi(List.of(
                tourApiItem("동쪽 전망대", "강원특별자치도 속초시 어딘가"),
                tourApiItem("서쪽 전망대", "전라남도 여수시 어딘가")));

        PlaceResolver.Result resolved = resolver.resolve("p1", "전망대", null, "강원", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("강원특별자치도 속초시 어딘가");
    }

    @Test
    void 끝일치가_여러건이고_regionHint로도_안_좁혀지면_되묻는다() {
        var resolver = resolverForTourApi(List.of(
                tourApiItem("동쪽 전망대", "강원특별자치도 속초시 어딘가"),
                tourApiItem("서쪽 전망대", "강원특별자치도 삼척시 어딘가")));

        PlaceResolver.Result resolved = resolver.resolve("p1", "전망대", null, "강원", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).hasSize(2);
    }

    @Test
    void 지점명없는_개인가게는_regionHint_기준점_주변_공공데이터_상호명으로_확정한다() {
        // 실측 사례("오문창순대국밥", 2026-11-04) - OSM(LocationIQ)엔 없고 공공상가정보엔
        // "오문창순대국밥식당"으로 등록돼 있다. 기존엔 LocationIQ 0건이라 NO_PLACE였다.
        var anchor = result("중리동, 대덕구, 대전광역시", 36.360, 127.421);
        var shop = new DataportalStoreClient.StoreResult("MA0001", "오문창순대국밥식당", null, "음식",
                "대전광역시 대덕구 한밭대로 1153", 36.359, 127.426);
        var resolver = resolverForAnchor(List.of(anchor), List.of(shop, store("다른국밥집")));

        PlaceResolver.Result resolved = resolver.resolve("p1", "오문창순대국밥", null, "대전 대덕구 중리동", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("대전광역시 대덕구 한밭대로 1153");
        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.DATAPORTAL);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA0001");
    }

    @Test
    void 지점명없는_개인가게는_regionHint가_구단위여도_VWorld_상호명_기준점으로_확정한다() {
        // 실측("오문창순대국밥", regionHint="대전 대덕구") - 구청 기준점에선 1.7km 밖이라 못 찾았다.
        // VWorld가 상호명으로 가게 좌표를 주면 그 주변에서 공공데이터 상호명으로 확정한다.
        var vworldAnchor = result("오문창순대국밥", 36.359, 127.426);
        var shop = new DataportalStoreClient.StoreResult("MA0001", "오문창순대국밥식당", null, "음식",
                "대전광역시 대덕구 한밭대로 1153", 36.359, 127.426);
        var resolver = resolverForVWorldAnchor(vworldAnchor, List.of(shop));

        PlaceResolver.Result resolved = resolver.resolve("p1", "오문창순대국밥", null, "대전 대덕구", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingProvider()).isEqualTo(GeocodingProvider.DATAPORTAL);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA0001");
    }

    // --- "구 단위 지역 + 가게명" VWorld 기준점 (2026-11-08, 성수맛집 BEST10 실측) ---
    // 반경검색 fake: 요청 좌표에서 100m 안에 있는 업소만 돌려준다(좌표 기준 동작을 실제처럼).
    private static NearbyStoreLookup storesNear(List<DataportalStoreClient.StoreResult> all) {
        return (lon, lat, radiusMeters) -> all.stream()
                .filter(s -> Math.abs(s.lat() - lat) < 0.0009 && Math.abs(s.lon() - lon) < 0.0011)
                .toList();
    }

    /** 검색어별로 정해진 VWorld 결과(여러 개)를 돌려주는 fake. findAnchor는 그중 1순위. */
    private static FranchiseAnchorLookup vworldByQuery(Map<String, List<PlaceSearchClient.PlaceSearchResult>> byQuery) {
        return new FranchiseAnchorLookup() {
            @Override
            public Optional<PlaceSearchClient.PlaceSearchResult> findAnchor(String query) {
                return byQuery.getOrDefault(query, List.of()).stream().findFirst();
            }

            @Override
            public List<PlaceSearchClient.PlaceSearchResult> findAnchors(String query, int limit) {
                return byQuery.getOrDefault(query, List.of()).stream().limit(limit).toList();
            }
        };
    }

    private static final DataportalStoreClient.StoreResult TIGER = new DataportalStoreClient.StoreResult(
            "MA-TIGER", "호랑이식탁", null, "음식", "서울특별시 성동구 성수이로 32-10", 37.5426, 127.0560);

    @Test
    void 가게명만으론_다른_지역이_1순위여도_지역을_붙인_VWorld_검색으로_기준점을_잡아_확정한다() {
        // 실측: "호랑이식탁" 1순위는 경기 성남 → 지역 필터에 걸려 버려지고, 구청 기준점은 가게에서 3km+.
        var resolver = new PlaceResolver(storesNear(List.of(TIGER)),
                (name, branch, region) -> List.of(result("성동구, 서울특별시", 37.5635, 127.0365)),
                vworldByQuery(Map.of(
                        "호랑이식탁", List.of(result("호랑이식탁", 37.3670, 127.1080)),
                        "서울특별시 성동구 호랑이식탁", List.of(result("호랑이식탁", 37.5426, 127.0560)))),
                name -> List.of(), 1000);

        PlaceResolver.Result resolved = resolver.resolve("p5", "호랑이식탁", null, "서울특별시 성동구", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA-TIGER");
    }

    @Test
    void 지역을_붙인_VWorld_결과_중_2순위에서만_가게가_맞아도_확정한다() {
        // 실측: "서울특별시 성동구 서울맛집" 1순위(아차산로7길 29)는 실제 가게와 1,075m, 2순위가 1m.
        var shop = new DataportalStoreClient.StoreResult("MA-SEOUL", "서울맛집", null, "음식",
                "서울특별시 성동구 성덕정15길 6-9", 37.5383, 127.0540);
        var resolver = new PlaceResolver(storesNear(List.of(shop)),
                (name, branch, region) -> List.of(),
                vworldByQuery(Map.of("서울특별시 성동구 서울맛집", List.of(
                        result("서울맛집", 37.5450, 127.0620), result("서울맛집", 37.5383, 127.0540)))),
                name -> List.of(), 1000);

        PlaceResolver.Result resolved = resolver.resolve("p3", "서울맛집", null, "서울특별시 성동구", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울특별시 성동구 성덕정15길 6-9");
    }

    // PR #70 리뷰(doheelab-coder): VWorld 전국 검색 1순위가 같은 시·도의 다른 지점이면 그 지점으로 확정됐다.
    private static final DataportalStoreClient.StoreResult STARBUCKS_HONGDAE = new DataportalStoreClient.StoreResult(
            "MA-HD", "스타벅스", "홍대", "음식", "서울특별시 마포구 양화로 1", 37.5563, 126.9236);

    private static PlaceResolver resolverWithHongdaeAsVWorldFirst() {
        return new PlaceResolver(storesNear(List.of(STARBUCKS_HONGDAE)), (name, branch, region) -> List.of(),
                vworldByQuery(Map.of("스타벅스", List.of(result("스타벅스 홍대", 37.5563, 126.9236)))),
                name -> List.of(), 1000);
    }

    @Test
    void VWorld로_찾은_가게_주소에_지역의_구동이_없으면_확정하지_않고_되묻는다() {
        PlaceResolver.Result resolved = resolverWithHongdaeAsVWorldFirst()
                .resolve("p1", "스타벅스", null, "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates()).extracting(ResolvedPlaceDto::address)
                .containsExactly("서울특별시 마포구 양화로 1");
    }

    @Test
    void 지역이_서울특별시처럼_정식_시도명이어도_구동으로만_확인한다() {
        // "서울특별시"를 구·동 단어로 남기면 서울 모든 주소가 통과해 확인이 무력해진다.
        PlaceResolver.Result resolved = resolverWithHongdaeAsVWorldFirst()
                .resolve("p1", "스타벅스", null, "서울특별시 강남구", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
    }

    @Test
    void 지역이_시도뿐이면_지역을_붙인_VWorld_검색을_하지_않는다() {
        var queried = new java.util.ArrayList<String>();
        FranchiseAnchorLookup vworld = new FranchiseAnchorLookup() {
            @Override
            public Optional<PlaceSearchClient.PlaceSearchResult> findAnchor(String query) {
                queried.add(query);
                return Optional.empty();
            }

            @Override
            public List<PlaceSearchClient.PlaceSearchResult> findAnchors(String query, int limit) {
                queried.add(query);
                return List.of();
            }
        };
        var resolver = new PlaceResolver(storesNear(List.of()), (name, branch, region) -> List.of(), vworld,
                name -> List.of(), 1000);

        resolver.resolve("p1", "호랑이식탁", null, "서울", List.of());

        assertThat(queried).containsExactly("호랑이식탁");
    }

    // 실측(2026-11-05) "스타벅스 강남역7번출구점" - 같은 기준점 주변 다른 스타벅스 하나를 같이 둔다.
    private static final PlaceSearchClient.PlaceSearchResult GANGNAM_ANCHOR = result("강남역", 37.4979, 127.0276);
    private static final DataportalStoreClient.StoreResult KSQUARE = new DataportalStoreClient.StoreResult(
            "MA-KSQ", "스타벅스", "케이스퀘어강남", "음식", "서울특별시 강남구 강남대로94길 10", 37.4996, 127.0283);

    @Test
    void 공공데이터_지점명에_끝_점이_없어도_지점명으로_확정한다() {
        // 공공상가정보는 brchNm을 "강남역7번출구"처럼 "점" 없이 등록한다 - 예전엔 "강남역7번출구점"과 안 맞아
        // 거리 폴백으로 엉뚱한 매장을 되물었다.
        var exit7 = new DataportalStoreClient.StoreResult("MA-EXIT7", "스타벅스", "강남역7번출구", "음식",
                "서울특별시 서초구 강남대로 385", 37.4963, 127.0287);
        var resolver = resolverForVWorldAnchor(GANGNAM_ANCHOR, List.of(KSQUARE, exit7));

        PlaceResolver.Result resolved = resolver.resolve("p1", "스타벅스", "강남역7번출구점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().address()).isEqualTo("서울특별시 서초구 강남대로 385");
    }

    @Test
    void 상호명_띄어쓰기가_달라도_지점명으로_확정한다() {
        // LOCALDATA는 "스타벅스 강남역 7번출구점"처럼 띄어 쓴다(brchNm은 항상 null).
        var exit7 = new DataportalStoreClient.StoreResult("LD-EXIT7", "스타벅스 강남역 7번출구점", null, "커피숍",
                "서울특별시 서초구 강남대로 385, 1층 (서초동)", 37.4963, 127.0287);
        var resolver = resolverForVWorldAnchor(GANGNAM_ANCHOR, List.of(KSQUARE, exit7));

        PlaceResolver.Result resolved = resolver.resolve("p1", "스타벅스", "강남역7번출구점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("LD-EXIT7");
    }

    @Test
    void 두_데이터소스에_같은_매장이_있으면_하나로_합쳐_확정한다() {
        // 공공상가정보·LOCALDATA가 같은 매장을 각자 돌려주면 예전엔 후보 2개 되묻기로 끝났다.
        // 공공상가정보(먼저 들어옴) 쪽을 남긴다.
        var fromDataportal = new DataportalStoreClient.StoreResult("MA-EXIT7", "스타벅스", "강남역7번출구", "음식",
                "서울특별시 서초구 강남대로 385", 37.4963, 127.0287);
        var fromLocalData = new DataportalStoreClient.StoreResult("LD-EXIT7", "스타벅스 강남역 7번출구점", null, "커피숍",
                "서울특별시 서초구 강남대로 385, 1층 (서초동)", 37.4963, 127.0287);
        var resolver = resolverForVWorldAnchor(GANGNAM_ANCHOR, List.of(KSQUARE, fromDataportal, fromLocalData));

        PlaceResolver.Result resolved = resolver.resolve("p1", "스타벅스", "강남역7번출구점", "서울 강남", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA-EXIT7");
    }

    @Test
    void 짧은_브랜드는_공백을_지워야_맞는_단어_중간_업소를_후보로_넣지_않는다() {
        // PR #70 리뷰(doheelab-coder): 공백만 지우면 "성공 차돌박이"가 "공차"로 맞아, 확정될 공차가 되묻기 2곳이 됐다.
        var anchor = result("성수동, 성동구, 서울특별시", 37.544, 127.055);
        var gongcha = new DataportalStoreClient.StoreResult("MA-GC", "공차", "성수역점", "음식",
                "서울특별시 성동구 아차산로 100", 37.544, 127.055);
        var chadol = new DataportalStoreClient.StoreResult("MA-CD", "성공 차돌박이", null, "음식",
                "서울특별시 성동구 아차산로 102", 37.544, 127.055);
        var resolver = resolverForAnchor(List.of(anchor), List.of(gongcha, chadol));

        PlaceResolver.Result resolved = resolver.resolve("p1", "공차", null, "서울 성수동", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA-GC");
    }

    @Test
    void 띄어쓰기만_다른_브랜드는_계속_같은_가게로_본다() {
        var anchor = result("성수동, 성동구, 서울특별시", 37.544, 127.055);
        var hau = new DataportalStoreClient.StoreResult("MA-HAU", "돼지곰탕하우", null, "음식",
                "서울특별시 성동구 성수이로 62", 37.544, 127.055);
        var resolver = resolverForAnchor(List.of(anchor), List.of(hau));

        PlaceResolver.Result resolved = resolver.resolve("p1", "돼지곰탕 하우", null, "서울 성수동", List.of());

        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.RESOLVED);
        assertThat(resolved.resolvedPlace().geocodingPlaceId()).isEqualTo("MA-HAU");
    }

    @Test
    void 지점명없는_가게가_기준점_주변_공공데이터에_없으면_기존_LocationIQ_흐름으로_폴백한다() {
        var anchor = result("중리동, 대덕구, 대전광역시", 36.360, 127.421);
        var resolver = resolverForAnchor(List.of(anchor), List.of(store("전혀다른가게")));

        var fallback = List.of(result("오문창순대국밥, 대전광역시", 36.359, 127.426));
        PlaceResolver.Result resolved = resolver.resolve("p1", "오문창순대국밥", null, "대전 대덕구 중리동", fallback);

        // 공공데이터 경로가 못 찾았으니 기존 흐름대로 - LocationIQ 1건, 반경 내 상호명 불일치라 되묻기.
        assertThat(resolved.decision()).isEqualTo(PlaceResolver.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.confirmRequest().candidates().get(0).geocodingProvider())
                .isEqualTo(GeocodingProvider.LOCATIONIQ);
    }
}
