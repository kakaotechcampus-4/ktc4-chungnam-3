package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * LocationIQ 검색 결과를 걸러서 장소를 확정할지, 사용자에게 되물을지, 확정할 수 없다고 볼지
 * 판단한다 (저장 파이프라인.md 3장 "판정" 기준, 도구 검토 문서 2026-09-23 "개선 ①" 반영).
 * <p>
 * 판정 순서: ⓪ 지점명이 있고 regionHint에 시·도보다 세부적인 지역명(구·동·역 등)이 있으면
 * {@link #resolveViaAnchor}를 먼저 시도한다 (2026-09-29 추가, 아래 설명). 실패하면 기존 순서로:
 * ① 결과 이름에 찾던 이름이 없으면 버림 ② 도로·동네급 결과(class=highway/place)는 버림 - 단
 * class=highway라도 type=bus_stop이면 살려둔다(개선⑤, 아래 설명) ③ 시·도가 명백히 다른 결과는 버림
 * ④ 남은 후보 중 서로 가까운 좌표는 중복 등록으로 보고 하나로 합친 뒤(개선④, {@link #dedupeByProximity})
 * 개수로 확정/되묻기/장소없음 판정, 이때 후보가 1건이어도
 * 지점명이 다르면, 또는 좌표 반경 내 실제 상가업소 중에 이름이 일치하는 게 하나도 없으면(아래
 * {@link #hasNearbyStoreMismatch} 참고) 확정하지 않고 되묻는다 (검색어 자체엔 지점명을 안 넣으므로 -
 * PlaceSearchClient 참고 - 지점 특정은 여기서 한다).
 * <p>
 * 시·도 단위 지역 교차검증(③)만으로는 같은 시·도 안에서 구·동 단위까지 틀린 동명이인(예: 송파의
 * 「을지로 골뱅이」)을 못 잡는다. 이걸 보완하려고 후보가 1건으로 좁혀졌을 때 {@link NearbyStoreLookup}
 * (공공 상가정보, 개선②)으로 좌표 반경 내 실제 업소 이름을 한 번 더 대조한다 - 반경 내에 상가업소
 * 데이터가 전혀 없으면(교통시설·공공기관처럼 애초에 상가업소로 등록 안 되는 장소일 수 있음) 판단을
 * 보류하고 그대로 두지만, 데이터는 있는데 이름이 하나도 안 맞으면 후보 좌표가 실제로는 다른 곳일
 * 가능성이 높다고 보고 되묻는다.
 * <p>
 * <b>프랜차이즈 지점(개선③, 2026-09-29)</b>: "스타벅스 강남역점"처럼 지점이 전국에 흩어진 브랜드는
 * ①~④만으로는 못 푼다 - LocationIQ에 "스타벅스 서울"만 물어보면(지점명은 검색어에서 뺌) 상위 몇 건이
 * 강남역과 무관한 지점일 수 있고, 지점명("강남역점")은 애초에 주소 텍스트에 안 나오는 상호 내부
 * 표기라 문자열로 못 찾는다. 그래서 순서를 뒤집는다 - 지점명에 딸려오는 지역 단서(regionHint의
 * 시·도 이후 부분, 예: "강남")로 **먼저 기준점(역·동네 등)을 LocationIQ로 정확히 찾고**(교통시설·
 * 랜드마크는 원래 정확도가 높음, LocationIQ 검색 정확도 테스트 참고), 그 좌표 주변을 공공 상가정보로
 * 뒤져서 상호명이 일치하는 실제 등록 업소를 찾는다. 기준점을 못 찾거나 주변에 상호명이 일치하는
 * 업소가 없으면 기존 ①~④ 흐름으로 그대로 폴백한다 - 이 경로는 어디까지나 "찾으면 더 좋은" 보강이지,
 * 기존 동작을 깨면 안 된다.
 * <p>
 * <b>지점명 표기 불일치 보완 (개선③ 후속, 2026-09-30)</b>: 공공 상가정보의 지점명(brchNm)이 실제
 * 소비자가 부르는 이름과 다르거나(예: "강남역점"을 찾는데 등록명은 "7번출구"), brchNm 없이 상호명
 * (bizesNm)에 지점명이 붙어있는 경우가 흔하다. 그래서 먼저 brchNm·bizesNm 텍스트로 좁혀보고, 그래도
 * 하나도 안 걸리면 지점명 매칭을 포기하고 **기준점에서 가장 가까운 곳**으로 좁힌다(브랜드명은 이미
 * 확인된 상태). 제일 가까운 곳이 확실히(수십m 이상) 더 가까우면 그곳으로 확정하고, 비슷하게 가까운
 * 곳이 여럿이면 되묻는다.
 * <p>
 * <b>중복 등록 병합(개선④, 2026-09-30)</b>: ①~④ 흐름에서 지역·클래스 필터를 통과한 후보들 중,
 * 같은 실제 장소가 출입구·건물·부지 단위로 중복 등록돼 서로 가까운 좌표로 여러 건 잡히는 경우가
 * 있다(예: "서울역", "롯데월드", "홍대입구역"). 개수만 보고 판정하기 전에 {@link #dedupeByProximity}로
 * 이런 중복을 먼저 하나로 합쳐서, 실제로는 한 곳인데 개수 상한에 걸려 NO_PLACE가 되거나 불필요하게
 * 되묻는 문제를 줄인다.
 * <p>
 * <b>지하철역 정류장 태그 예외(개선⑤, 2026-09-30)</b>: "강남역"·"역삼역"·"선릉역"을 검색하면
 * LocationIQ가 주는 후보가 전부 class=highway/type=bus_stop(버스 정류장 태그)뿐인 경우가 실측으로
 * 확인됐다 - OSM에 해당 역의 건물·출입구가 별도 태그로 없고, 역 이름이 붙은 버스 정류장 노드만
 * 있다는 뜻. 기존엔 class=highway를 통째로 걸러서 이런 경우 후보가 0건이 되어 그대로 NO_PLACE였다.
 * 이름 필터를 이미 통과한 결과라 "그 이름이 붙은 실제 지점"이라는 신뢰도는 업체 결과와 다르지 않으므로,
 * bus_stop만 예외로 살려둔다({@link #ALLOWED_HIGHWAY_TYPE}) - 실제 도로 구간(주거도로·간선도로 등)은
 * 여전히 걸러진다.
 * <p>
 * <b>VWorld 기준점 보정(개선③ 후속, 2026-10-06)</b>: LocationIQ가 매장을 지오코딩 자체를 못 하거나
 * (예: "이디야커피 홍대청기와점"), 공공 상가정보의 상호명 표기가 브랜드명과 달라서(예: 등록명
 * "이디야홍대청기와점"엔 "커피"가 없음) brandMatches 문자열 비교가 애초에 실패하는 경우가 실측으로
 * 확인됐다. {@link #resolveViaAnchor}는 이제 LocationIQ 기준점을 시도하기 전에 먼저
 * {@link #resolveViaVWorldAnchor}(브이월드 검색)를 시도한다 - 브랜드+지점명으로 바로 정확한 좌표를
 * 구해 공공 상가정보 반경검색(좁은 반경)의 중심점으로만 쓰고, 응답 자체는 저장하지 않는다(VWorld
 * 이용약관 제19조 6항 "사전 승낙 없이 저장 금지" 때문 - 실제 저장값은 항상 공공 상가정보 자체
 * 데이터). 못 찾으면 기존 LocationIQ 기준점 흐름으로 그대로 폴백한다.
 * <p>
 * <b>다건 후보 지점명 교차검증(2026-10-06)</b>: 기준점 경로를 안 타는 일반 쿼리에서 후보가 2~3건으로
 * 걸렸을 때, 지점명이 있으면 기존엔(후보 1건일 때만 하던 걸) 활용 못 하고 무조건 되묻기로 갔다
 * (실측 사례: "성심당 대전 본점" - 대전에 실제로 지점이 3곳 있어서 전부 되묻기 후보로 나감). 이제는
 * 2~3건이어도 각 후보 반경 내 공공 상가정보에서 지점명이 일치하는 곳이 정확히 하나면 그걸로 확정한다
 * ({@link #findCandidateWithBranch}).
 */
@Slf4j
@Component
public class PlaceResolver {

    private static final int MAX_CONFIRMATION_CANDIDATES = 3;

    // 도로·동네·행정구역급 결과는 업체가 아니므로 확정 후보에서 제외한다.
    private static final Set<String> REJECTED_CLASSES = Set.of("highway", "place");

    // class=highway라도 type=bus_stop은 예외로 살려준다(개선⑤, 2026-09-30). 실측 확인: "강남역"·
    // "역삼역"·"선릉역"처럼 지하철역을 검색하면 LocationIQ가 반환하는 후보가 전부 정류장(bus_stop)
    // 태그뿐인 경우가 흔하다 - 실제로 그 역을 가리키는 정확한 결과인데 "도로"로 오인해 통째로 걸러져
    // NO_PLACE가 됐다. bus_stop은 실제 도로 구간(주거도로·간선도로 등)이 아니라 "이름 붙은 지점"이라
    // 이름 필터(containsIgnoreCase)를 이미 통과했다면 업체 결과와 마찬가지로 신뢰할 수 있다.
    private static final String ALLOWED_HIGHWAY_TYPE = "bus_stop";

    // 「을지로 골뱅이→잠실」류 구·동 단위 오매칭을 잡기 위한 반경. 상가업소 등록 주소와 실제 좌표 사이
    // 오차를 감안해 너무 좁게 잡지 않되, 동 단위를 넘어갈 만큼 넓게 잡지도 않는다 - 팀 검토 필요한
    // 기본값(다른 임계값들과 마찬가지로 실측 데이터로 조정 가능).
    private static final int NEARBY_STORE_RADIUS_METERS = 100;

    // 기준점(역·동네 등) 주변에서 프랜차이즈 지점을 찾는 반경 - 위보다 넓게 잡는다. 기준점은 "정확한
    // 지점 좌표"가 아니라 "그 근방을 대표하는 좌표"(예: 역 출구 하나)라 오차가 더 클 수 있어서다.
    // 300 → 1000으로 조정(2026-10-02, 실측 근거): "교보문고 광화문점"이 기준점("광화문" 역 주변 좌표)
    // 에서 500m 이상 떨어져 있어 300m로는 반경 밖이라 NO_PLACE였다 - 공공데이터엔 실제로 "오렌즈교보문고
    // /광화문점"이라는 정확한 주소로 등록돼 있었는데도 반경 때문에 못 찾은 사례. `application.yaml`
    // 프로퍼티로 빼서 과도하게 넓혀 무관한 업소가 개수 상한에 걸리는 부작용이 보이면 숫자만 조정한다.
    private final int franchiseAnchorRadiusMeters;

    // VWorld 기준점(resolveViaVWorldAnchor) 전용 반경 - 위보다 훨씬 좁다. VWorld는 "그 근방 대표 좌표"가
    // 아니라 브랜드+지점명으로 이미 특정 매장을 찾은 좌표라(실측: "이디야홍대청기와점"이 정확히 그
    // 주소로 반환됨) 넓은 반경이 필요 없고, 오히려 넓히면 무관한 이웃 상가가 섞여 들어온다. 100m는
    // DUPLICATE_MERGE_RADIUS_METERS와 같은 근거(실측상 "같은 장소"와 "다른 장소"를 가르는 안전지대)를
    // 재사용한 값이다.
    private static final int VWORLD_ANCHOR_RADIUS_METERS = 100;

    // 지점명 텍스트로 못 좁혔을 때(개선③ 후속, 2026-09-30) 거리로 대신 좁힌다 - 가장 가까운 곳과
    // 그 다음으로 가까운 곳의 차이가 이 값보다 작으면 "확실히 더 가까운 곳"이 없다고 보고 되묻는다.
    private static final double DISTANCE_TIE_MARGIN_METERS = 30.0;

    // LocationIQ 후보 중 "같은 실제 장소가 중복 등록"된 경우를 하나로 합치는 반경(개선④, 2026-09-30).
    // 실측(50개 검색어 재검증) 기준: 확실한 중복 등록(같은 역 출입구를 서로 다른 OSM way로 중복 등록한
    // 경우 등)은 전부 200m 안이었고(예: "광화문"의 지하철역 출입구 2건이 2.8m 차이, "롯데월드" 2건이
    // 43.9m, "홍대입구역" 2건이 72.2m, "예술의전당" 2건이 49.2m, "서울역" 인접 출입구들이 73~140m),
    // 반대로 이름은 같지만 실제로 다른 장소인 경우는 전부 478m 이상 떨어져 있었다(예: "경복궁"의 궁궐과
    // 경복궁역이 516m, "광화문"의 실제 문 건물과 광화문역이 478m). 100m는 이 둘 사이 안전지대에 있는
    // 보수적인 값 - 애매하면 합치지 않고 그대로 되묻는 쪽이 안전하다(filterByRegion과 같은 원칙).
    private static final double DUPLICATE_MERGE_RADIUS_METERS = 100.0;

    // "CU"처럼 브랜드명이 짧아서 대분류(G2) 반경검색 + 상호명 부분일치만으론 무관한 업소까지 과매칭되던
    // 브랜드 전용 목록(2026-10-08, 실측으로 CU 과매칭 15건 확인 후 추가) - 이 목록에 있으면 업종
    // 소분류 코드로 먼저 좁힌다. G20405(편의점) 하나만 둔다 - GS25 일부 지점이 소분류상 "슈퍼마켓"으로
    // 등록된 사례가 실측으로 확인됐으나(ponytail: 이 경우는 여전히 못 잡음, 슈퍼마켓 소분류 코드 확정되면
    // 추가), CU는 전부 G20405였다.
    private static final Set<String> CONVENIENCE_STORE_BRANDS =
            Set.of("CU", "GS25", "지에스25", "세븐일레븐", "이마트24");
    private static final List<String> CONVENIENCE_STORE_SUBCATEGORY_CODES = List.of("G20405");

    // 공공상가정보엔 "CU"가 로마자가 아니라 한글 음차("씨유")로 등록돼 있어(2026-10-08 실측 확인) -
    // Gemini가 영상에서 뽑아내는 이름은 간판 그대로 로마자 "CU"일 걸로 예상되는데, 글자가 아예 안
    // 겹쳐서 containsIgnoreCase로는 영원히 매칭이 안 된다. 이런 표기 불일치가 또 나오면 여기 추가한다.
    private static final Map<String, String> BRAND_NAME_ALIASES = Map.of("CU", "씨유");

    private final NearbyStoreLookup nearbyStoreLookup;
    private final PlaceLookup placeLookup;
    private final FranchiseAnchorLookup franchiseAnchorLookup;

    public PlaceResolver(
            NearbyStoreLookup nearbyStoreLookup,
            PlaceLookup placeLookup,
            FranchiseAnchorLookup franchiseAnchorLookup,
            @Value("${franchise.anchor-radius-meters:1000}") int franchiseAnchorRadiusMeters) {
        this.nearbyStoreLookup = nearbyStoreLookup;
        this.placeLookup = placeLookup;
        this.franchiseAnchorLookup = franchiseAnchorLookup;
        this.franchiseAnchorRadiusMeters = franchiseAnchorRadiusMeters;
    }

    public enum Decision {
        RESOLVED,
        NEEDS_CONFIRMATION,
        NO_PLACE
    }

    public record Result(Decision decision, ResolvedPlaceDto resolvedPlace, ConfirmRequestDto confirmRequest) {
    }

    public Result resolve(String candidateId, String name, String branchName, String regionHint,
                           List<PlaceSearchClient.PlaceSearchResult> searchResults) {
        if (branchName != null && !branchName.isBlank()) {
            Optional<Result> viaAnchor = resolveViaAnchor(candidateId, name, branchName, regionHint);
            if (viaAnchor.isPresent()) {
                return viaAnchor.get();
            }
        }

        List<PlaceSearchClient.PlaceSearchResult> filtered =
                dedupeByProximity(filterByRegion(regionHint, filterByNameAndClass(name, searchResults)));

        if (filtered.isEmpty() || filtered.size() > MAX_CONFIRMATION_CANDIDATES) {
            return new Result(Decision.NO_PLACE, null, null);
        }

        if (filtered.size() == 1) {
            PlaceSearchClient.PlaceSearchResult match = filtered.get(0);

            boolean branchMatchesOrUnspecified = branchName == null || branchName.isBlank()
                    || containsIgnoreCase(match.displayName(), branchName);

            if (branchMatchesOrUnspecified && !hasNearbyStoreMismatch(name, match)) {
                ResolvedPlaceDto place = new ResolvedPlaceDto(
                        candidateId, name, branchName, match.displayName(), match.lat(), match.lon());
                return new Result(Decision.RESOLVED, place, null);
            }

            // 이름은 맞는데 찾던 지점인지(지점명 불일치), 또는 이 좌표가 실제로 맞는 곳인지
            // (반경 내 상가업소 이름 불일치) 확신할 수 없으니 확정하지 않고 되묻는다.
            ConfirmRequestDto confirm = new ConfirmRequestDto("어느 장소가 맞을까요?", List.of(
                    new ResolvedPlaceDto(candidateId + "-1", name, branchName, match.displayName(), match.lat(), match.lon())
            ));
            return new Result(Decision.NEEDS_CONFIRMATION, null, confirm);
        }

        if (branchName != null && !branchName.isBlank()) {
            Optional<PlaceSearchClient.PlaceSearchResult> matched = findCandidateWithBranch(branchName, filtered);
            if (matched.isPresent()) {
                PlaceSearchClient.PlaceSearchResult match = matched.get();
                ResolvedPlaceDto place = new ResolvedPlaceDto(
                        candidateId, name, branchName, match.displayName(), match.lat(), match.lon());
                return new Result(Decision.RESOLVED, place, null);
            }
        }

        List<ResolvedPlaceDto> options = IntStream.range(0, filtered.size())
                .mapToObj(i -> {
                    PlaceSearchClient.PlaceSearchResult match = filtered.get(i);
                    return new ResolvedPlaceDto(
                            candidateId + "-" + (i + 1), name, branchName, match.displayName(), match.lat(), match.lon());
                })
                .toList();
        ConfirmRequestDto confirm = new ConfirmRequestDto("어느 장소가 맞을까요?", options);
        return new Result(Decision.NEEDS_CONFIRMATION, null, confirm);
    }

    /**
     * 후보가 2~3건으로 걸렸을 때(2026-10-06 추가) 지점명이 있으면 공공 상가정보로 교차검증해서
     * 좁힌다. 실측 사례("성심당 대전 본점"): LocationIQ가 대전 내 성심당 지점 3곳(중구 본점·유성구점·
     * 동구 대전역점 추정)을 모두 돌려주는데, 기존엔 지점명 확인을 후보가 1건일 때만 해서 이런 경우
     * 지점명 정보가 있어도 못 쓰고 무조건 되묻기로 갔다. 정확히 한 후보의 반경 내에서만 지점명이
     * 일치하면 그걸로 확정하고, 0건이거나 2건 이상에서 일치하면 확신할 수 없으니 빈 값을 반환해
     * 기존 전체 후보 되묻기로 넘어간다.
     */
    private Optional<PlaceSearchClient.PlaceSearchResult> findCandidateWithBranch(
            String branchName, List<PlaceSearchClient.PlaceSearchResult> candidates) {
        List<PlaceSearchClient.PlaceSearchResult> matches = candidates.stream()
                .filter(c -> hasNearbyBranchMatch(branchName, c))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private boolean hasNearbyBranchMatch(String branchName, PlaceSearchClient.PlaceSearchResult candidate) {
        List<DataportalStoreClient.StoreResult> nearby;
        try {
            nearby = nearbyStoreLookup.searchByRadius(candidate.lon(), candidate.lat(), NEARBY_STORE_RADIUS_METERS);
        } catch (RuntimeException e) {
            return false;
        }
        return nearby.stream().anyMatch(store ->
                containsIgnoreCase(store.brchNm(), branchName) || containsIgnoreCase(store.bizesNm(), branchName));
    }

    /**
     * 결과 이름에 찾던 이름이 없으면, 그리고 도로·동네급 결과(class=highway/place)면 제외한다.
     * 단 class=highway/type=bus_stop({@link #ALLOWED_HIGHWAY_TYPE})은 예외 - 위 상수 설명 참고.
     */
    private List<PlaceSearchClient.PlaceSearchResult> filterByNameAndClass(
            String name, List<PlaceSearchClient.PlaceSearchResult> results) {
        return results.stream()
                .filter(r -> containsIgnoreCase(r.displayName(), name))
                .filter(r -> !isRejectedClass(r))
                .toList();
    }

    private boolean isRejectedClass(PlaceSearchClient.PlaceSearchResult result) {
        if (result.osmClass() == null || !REJECTED_CLASSES.contains(result.osmClass().toLowerCase())) {
            return false;
        }
        return !("highway".equalsIgnoreCase(result.osmClass()) && ALLOWED_HIGHWAY_TYPE.equalsIgnoreCase(result.osmType()));
    }

    /**
     * regionHint(시·도 단위)와 검색 결과 주소가 겹치는지 확인해 명백히 다른 시·도인 결과를 제외한다.
     * regionHint가 없거나 매핑표에 없는(구·동 단위 등) 지역명이면 교차검증을 건너뛰고 그대로 반환한다 -
     * 모르는 걸 걸러내려다 오히려 잘못 걸러내는 것보다 안전하다.
     * <p>
     * {@link PlaceSearchClient}가 {@code accept-language=ko}를 쓰면서(2026-09-29) displayName이
     * 한글로 오게 됐다 - {@link KoreanProvinces#displayAliasesFor}가 한글 표기(대부분 줄임말 자체)와
     * 로마자 표기(하위호환)를 같이 주므로 그걸 그대로 쓴다.
     */
    private List<PlaceSearchClient.PlaceSearchResult> filterByRegion(
            String regionHint, List<PlaceSearchClient.PlaceSearchResult> results) {
        List<String> aliases = regionAliases(regionHint);
        if (aliases.isEmpty()) {
            return results;
        }
        return results.stream()
                .filter(r -> aliases.stream().anyMatch(alias -> containsIgnoreCase(r.displayName(), alias)))
                .toList();
    }

    /**
     * {@link #filterByRegion}과 같은 기준(시·도 별칭 교차검증)을 공공상가정보 주소(roadAddress)에
     * 적용한다 - {@link #resolveViaVWorldAnchor}가 VWorld 기준점이 엉뚱한 지역으로 잘못 잡히는 걸
     * 잡아내는 용도. VWorld 응답 자체엔 주소가 없어서(의도적, VWorldSearchClient 참고) 여기서 거른다.
     */
    private List<DataportalStoreClient.StoreResult> filterStoresByRegion(
            String regionHint, List<DataportalStoreClient.StoreResult> stores) {
        List<String> aliases = regionAliases(regionHint);
        if (aliases.isEmpty()) {
            return stores;
        }
        return stores.stream()
                .filter(s -> s.roadAddress() != null
                        && aliases.stream().anyMatch(alias -> containsIgnoreCase(s.roadAddress(), alias)))
                .toList();
    }

    private List<String> regionAliases(String regionHint) {
        if (regionHint == null || regionHint.isBlank()) {
            return List.of();
        }
        return KoreanProvinces.ROMANIZED_ALIASES.keySet().stream()
                .filter(regionHint::contains)
                .flatMap(key -> KoreanProvinces.displayAliasesFor(key).stream())
                .toList();
    }

    /**
     * 서로 {@link #DUPLICATE_MERGE_RADIUS_METERS} 안에 있는 후보들을 한 그룹으로 묶어 그룹당 하나만
     * 남긴다(개선④). LocationIQ/OSM은 같은 실제 장소(특히 역·큰 시설)를 출입구·건물·부지 등으로 나눠
     * 중복 등록해두는 경우가 흔한데, 지금까지는 이걸 "서로 다른 후보"로 세서 개수 상한에 걸리거나
     * (예: "서울역") 불필요하게 되묻는(예: "롯데월드") 원인이 됐다. 정확히 같은 좌표가 아니라 조금씩
     * 떨어져 있어 단순 좌표 비교로는 못 걸러내므로, 두 후보 사이 거리로 판단한다.
     * <p>
     * 그룹 묶기는 A-B, B-C가 각각 문턱 안이면 A-C가 문턱보다 멀어도 한 그룹으로 합치는 전이적(transitive)
     * 방식이다 - 그래야 "서울역"처럼 여러 출입구가 사슬처럼 이어진 경우를 하나로 모을 수 있다. 그룹의
     * 대표값은 원래 순서(LocationIQ 자체 관련도 순위)상 가장 먼저 나온 후보를 쓴다.
     */
    private List<PlaceSearchClient.PlaceSearchResult> dedupeByProximity(
            List<PlaceSearchClient.PlaceSearchResult> results) {
        int n = results.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                PlaceSearchClient.PlaceSearchResult a = results.get(i);
                PlaceSearchClient.PlaceSearchResult b = results.get(j);
                if (distanceMeters(a.lat(), a.lon(), b.lat(), b.lon()) < DUPLICATE_MERGE_RADIUS_METERS) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, PlaceSearchClient.PlaceSearchResult> representativeByGroup = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            representativeByGroup.putIfAbsent(find(parent, i), results.get(i));
        }
        return List.copyOf(representativeByGroup.values());
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootA] = rootB;
        }
    }

    /**
     * 후보 좌표 반경 내 실제 상가업소 중 이름이 일치하는 게 하나도 없으면 true(오매칭 의심).
     * 반경 내에 상가업소 데이터 자체가 없으면(교통시설·공공기관 등 이 데이터셋에 애초에 안 잡히는
     * 장소일 수 있음) false로 처리한다 - 데이터가 없다고 없는 장소로 단정하면 안 된다.
     * 공공 상가정보 조회는 어디까지나 보조 신호라, 조회 자체가 실패해도(네트워크 오류 등) 기존
     * LocationIQ 판정을 막지 않고 false로 넘어간다.
     */
    private boolean hasNearbyStoreMismatch(String name, PlaceSearchClient.PlaceSearchResult candidate) {
        List<DataportalStoreClient.StoreResult> nearby;
        try {
            nearby = nearbyStoreLookup.searchByRadius(candidate.lon(), candidate.lat(), NEARBY_STORE_RADIUS_METERS);
        } catch (RuntimeException e) {
            log.warn("공공 상가정보 조회 실패 - 반경 검증 없이 확정합니다: {}", e.getMessage());
            return false;
        }

        if (nearby.isEmpty()) {
            return false;
        }
        return nearby.stream().noneMatch(store -> matchesBrand(store.bizesNm(), name));
    }

    /**
     * regionHint에서 시·도를 뺀 나머지("서울 강남" → "강남")를 기준점 검색어로 뽑는다. 시·도만 있고
     * 그 이상 정보가 없으면(예: "대전"만) 기준점을 특정할 수 없으니 빈 값을 반환한다 - 이 경우
     * 호출부가 기존 ①~④ 흐름으로 폴백한다. 토큰 단위로 비교해서 "서울대입구"처럼 시·도 이름을
     * 접두어로 포함하는 다른 지명까지 잘못 잘라내지 않는다.
     */
    private Optional<String> extractAnchorQuery(String regionHint) {
        if (regionHint == null || regionHint.isBlank()) {
            return Optional.empty();
        }

        List<String> tokens = List.of(regionHint.trim().split("\\s+"));
        String remainder = tokens.stream()
                .filter(token -> !KoreanProvinces.ROMANIZED_ALIASES.containsKey(token))
                .collect(Collectors.joining(" "));

        return remainder.isBlank() ? Optional.empty() : Optional.of(remainder);
    }

    /**
     * VWorld 검색으로 브랜드+지점명의 정확한 좌표를 구해 기준점으로 쓴다(2026-10-06 추가) - 아래
     * LocationIQ 기준점(지역명만으로 근방 좌표를 구하는 기존 방식)보다 훨씬 정밀하다(실측:
     * "이디야커피 홍대청기와점"으로 VWorld를 검색하면 정확히 그 주소가 반환됨 - LocationIQ는 이
     * 매장을 지오코딩 자체를 못 했음). 그래서 반경도 훨씬 좁게({@link #VWORLD_ANCHOR_RADIUS_METERS})
     * 잡는다. VWorld 응답은 좌표만 쓰고 저장하지 않는다({@link FranchiseAnchorLookup} 참고) - 실제
     * 저장값은 항상 아래 공공 상가정보 반경검색 결과다.
     * <p>
     * 브랜드명이 "이디야커피"인데 실제 등록명은 "이디야홍대청기와점"처럼 "커피"가 빠져있는 등
     * 공공 상가정보 표기가 달라 상호명 문자열 비교가 애초에 실패하는 경우가 실측으로 확인됐다.
     * 다만 이건 일부 브랜드 얘기지 전부가 아니다 - 올리브영("씨제이올리브영명동점")·교보문고
     * ("오렌즈교보문고광화문점")는 오히려 상호명 문자열에 브랜드명이 그대로 들어있어서, 상호명
     * 확인을 생략하고 거리만 보면 반경 안 다른 업종 업소가 섞여 들어와 멀쩡히 되던 확정이 되묻기로
     * 나빠지는 회귀가 실측으로 확인됐다(2026-10-06, 올리브영 명동점 사례). 그래서 여기도 아래
     * {@link #resolveViaAnchor}와 같은 "상호명 먼저, 안 되면 거리" 순서를({@link
     * #narrowByBrandThenDistance}) 그대로 재사용한다 - 반경만 훨씬 좁을 뿐 좁히는 방식은 동일하다.
     */
    private Optional<Result> resolveViaVWorldAnchor(String candidateId, String name, String branchName, String regionHint) {
        String query = branchName == null || branchName.isBlank() ? name : name + " " + branchName;

        Optional<PlaceSearchClient.PlaceSearchResult> anchor;
        try {
            anchor = franchiseAnchorLookup.findAnchor(query);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (anchor.isEmpty()) {
            return Optional.empty();
        }

        List<DataportalStoreClient.StoreResult> nearby;
        try {
            nearby = nearbyStores(name, anchor.get().lon(), anchor.get().lat(), VWORLD_ANCHOR_RADIUS_METERS);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        // PR 리뷰(doheelab-coder)로 발견된 버그 수정 - VWorld가 같은 지점명("중앙점" 등)을 regionHint와
        // 무관한 다른 시·도에서 찾아줘도 그대로 기준점으로 믿어버려서, 그 반경 안 업소를 엉뚱한 지역의
        // 지점으로 확정해버리는 사례가 있었다. VWorld 응답 자체(좌표만 있고 주소 없음, 약관상 의도적으로
        // 그렇게 만들어둠 - VWorldSearchClient 참고)는 교차검증에 못 쓰므로, 대신 이미 저장 가능한
        // 공공상가정보 주소(roadAddress)로 regionHint를 교차검증한다 - 기준점이 엉뚱한 지역이면 그 반경
        // 안 업소들의 주소도 전부 엉뚱한 지역이라 여기서 걸러진다.
        nearby = filterStoresByRegion(regionHint, nearby);
        if (nearby.isEmpty()) {
            return Optional.empty();
        }

        List<DataportalStoreClient.StoreResult> brandMatches = nearby.stream()
                .filter(s -> matchesBrand(s.bizesNm(), name))
                .toList();
        if (brandMatches.isEmpty()) {
            // PR 리뷰(doheelab-coder)로 발견된 버그 수정(2026-10-09) - 반경 안 업소가 지역은 맞는데
            // 브랜드가 확인되는 곳이 하나도 없을 때, 예전엔 "상호명 표기 불일치(이디야류)일 수 있다"며
            // 브랜드 무관 전체(nearby)를 narrowByBrandThenDistance에 그대로 넘겼다 - 그 결과 반경 안에
            // 전혀 다른 가게만 있어도 가장 가깝다는 이유로 그 가게가 찾던 브랜드로 확정돼버렸다(예:
            // "CU 중구청점"을 찾는데 반경 안엔 전혀 다른 가게만 있어도 그게 CU로 확정됨). 브랜드명
            // 대신 지점명(branchName) 텍스트로 한 번 더 좁혀본다 - 이디야류(등록명엔 "커피"가 없지만
            // 지점명 "홍대청기와점"은 그대로 들어있는 경우)는 이걸로 걸리니 확정 대신 되묻는다. 지점명
            // 텍스트로도 전혀 안 걸리거나 너무 많이 걸리면(개수 상한 초과) 이 기준점 자체를 못 믿겠다는
            // 뜻이니 포기하고 기존 ①~④ 흐름으로 폴백한다.
            List<DataportalStoreClient.StoreResult> byBranch = nearby.stream()
                    .filter(s -> containsIgnoreCase(s.brchNm(), branchName) || containsIgnoreCase(s.bizesNm(), branchName))
                    .toList();
            if (byBranch.isEmpty() || byBranch.size() > MAX_CONFIRMATION_CANDIDATES) {
                return Optional.empty();
            }
            return Optional.of(confirmOnly(candidateId, byBranch));
        }

        return narrowByBrandThenDistance(
                candidateId, name, branchName, anchor.get().lat(), anchor.get().lon(), brandMatches);
    }

    /**
     * 프랜차이즈 지점 판정(개선③) - regionHint의 세부 지역명으로 기준점을 먼저 찾고, 그 주변을
     * 공공 상가정보로 뒤져서 상호명이 일치하는 실제 업소를 찾는다. 기준점을 못 찾거나, 기준점은
     * 찾았는데 주변에 상호명이 일치하는 업소가 하나도 없으면 빈 값을 반환한다 - 이 경우 호출부가
     * 기존 ①~④ 흐름으로 폴백하므로, 이 메서드는 "찾으면 확정/되묻기, 못 찾으면 모르겠다"만 답한다.
     * VWorld 기준점({@link #resolveViaVWorldAnchor})을 먼저 시도하고, 못 찾을 때만 여기(LocationIQ
     * 기준점)로 폴백한다.
     */
    private Optional<Result> resolveViaAnchor(String candidateId, String name, String branchName, String regionHint) {
        Optional<Result> viaVWorldAnchor = resolveViaVWorldAnchor(candidateId, name, branchName, regionHint);
        if (viaVWorldAnchor.isPresent()) {
            return viaVWorldAnchor;
        }

        Optional<String> anchorQuery = extractAnchorQuery(regionHint);
        if (anchorQuery.isEmpty()) {
            return Optional.empty();
        }

        List<PlaceSearchClient.PlaceSearchResult> anchorCandidates;
        try {
            anchorCandidates = placeLookup.search(anchorQuery.get(), null, regionHint);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        // 기준점은 "정확한 업체"가 아니라 "그 근방 좌표"만 있으면 되므로, class 필터는 안 걸고
        // 이름만 대충 맞으면 첫 번째 것을 쓴다 - 같은 역 이름의 여러 표기(출구별 등)는 서로 몇십m
        // 안쪽이라 반경 검색에 지장 없음.
        Optional<PlaceSearchClient.PlaceSearchResult> anchor = anchorCandidates.stream()
                .filter(c -> containsIgnoreCase(c.displayName(), anchorQuery.get()))
                .findFirst();
        if (anchor.isEmpty()) {
            return Optional.empty();
        }

        List<DataportalStoreClient.StoreResult> nearby;
        try {
            nearby = nearbyStores(name, anchor.get().lon(), anchor.get().lat(), franchiseAnchorRadiusMeters);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        List<DataportalStoreClient.StoreResult> brandMatches = nearby.stream()
                .filter(s -> matchesBrand(s.bizesNm(), name))
                .toList();
        if (brandMatches.isEmpty()) {
            return Optional.empty();
        }

        return narrowByBrandThenDistance(
                candidateId, name, branchName, anchor.get().lat(), anchor.get().lon(), brandMatches);
    }

    /**
     * 기준점(VWorld/LocationIQ 공용) 주변 상가업소를 조회한다 - {@link #CONVENIENCE_STORE_BRANDS}에
     * 있는 브랜드는 업종 소분류 코드로 먼저 좁히고(2026-10-08, CU 과매칭 수정), 나머지는 기존처럼
     * 음식(I2)·소매(G2) 대분류로 좁힌다. 다른 업종 브랜드는 이 목록에 없으면 여전히 못 찾을 수 있음 -
     * 팀 검토 필요한 임시 목록.
     */
    private List<DataportalStoreClient.StoreResult> nearbyStores(
            String name, double lon, double lat, int radiusMeters) {
        if (CONVENIENCE_STORE_BRANDS.stream().anyMatch(brand -> containsIgnoreCase(name, brand))) {
            return nearbyStoreLookup.searchByRadiusBySubCategory(
                    lon, lat, radiusMeters, CONVENIENCE_STORE_SUBCATEGORY_CODES);
        }
        return nearbyStoreLookup.searchByRadius(lon, lat, radiusMeters, List.of("I2", "G2"));
    }

    /**
     * 브랜드로 좁혀진 후보(brandMatches)를 지점명 텍스트로 먼저 좁히고(1차), 전혀 안 좁혀지면
     * 기준점에서의 거리로 좁힌다(2차) - {@link #resolveViaAnchor}(LocationIQ 기준점)와
     * {@link #resolveViaVWorldAnchor}(VWorld 기준점) 공용 로직(2026-10-06 추출, 원래
     * resolveViaAnchor에만 있던 걸 VWorld 쪽에도 그대로 적용하려고 분리함 - 상호명 확인을 생략하고
     * 거리만 보면 올리브영·교보문고처럼 원래 텍스트 매칭이 잘 되던 브랜드가 오히려 반경 안 다른
     * 업종 업소에 밀려 되묻기로 나빠지는 회귀가 실측으로 확인됐었다).
     */
    private Optional<Result> narrowByBrandThenDistance(
            String candidateId, String name, String branchName, double anchorLat, double anchorLon,
            List<DataportalStoreClient.StoreResult> brandMatches) {
        // 1차: 지점명(brchNm) 또는 상호명(bizesNm)에 지점명 텍스트가 그대로 들어있는지 본다.
        // 공공 상가정보는 지점명을 brchNm에 따로 안 두고 bizesNm에 붙여서 등록한 경우가 많다
        // (예: "스타벅스강남역점"인데 brchNm은 비어있음) - 그래서 둘 다 본다.
        List<DataportalStoreClient.StoreResult> narrowed = brandMatches.stream()
                .filter(s -> containsIgnoreCase(s.brchNm(), branchName) || containsIgnoreCase(s.bizesNm(), branchName))
                .toList();
        if (!narrowed.isEmpty()) {
            // "CU" 같은 짧은 브랜드명·코드는 containsIgnoreCase가 반경 내 무관한 업소까지 폭넓게
            // 걸러내는 경우가 실측으로 확인됐다(15건) - 개수 상한을 넘으면 되묻기도 의미가 없으니
            // 최상위 ①~④ 흐름과 같은 기준(MAX_CONFIRMATION_CANDIDATES)으로 NO_PLACE 처리한다.
            if (narrowed.size() > MAX_CONFIRMATION_CANDIDATES) {
                return Optional.of(new Result(Decision.NO_PLACE, null, null));
            }
            return Optional.of(buildResult(candidateId, name, branchName, narrowed));
        }

        // 2차: 텍스트로 전혀 안 좁혀지면(예: 등록명이 "강남역점"이 아니라 "7번출구"처럼 완전히 다른
        // 표기라 문자열 매칭 자체가 불가능한 경우) 지점명 대신 "기준점에서 가장 가까운 곳"으로 좁힌다.
        // 브랜드명은 이미 확인했으니, 여기서는 거리만 본다. 제일 가까운 곳이 그 다음으로 가까운 곳보다
        // 확실히(DISTANCE_TIE_MARGIN_METERS 이상) 가까우면 그곳으로 확정하고, 비슷하게 가까운 곳이
        // 여럿이면(같은 건물에 여러 지점 등) 확신할 수 없으면 되묻는다.
        List<DataportalStoreClient.StoreResult> byDistance = brandMatches.stream()
                .sorted(Comparator.comparingDouble(s -> distanceMeters(anchorLat, anchorLon, s.lat(), s.lon())))
                .toList();

        if (byDistance.size() == 1) {
            return Optional.of(buildResult(candidateId, name, branchName, byDistance));
        }

        double closest = distanceMeters(anchorLat, anchorLon, byDistance.get(0).lat(), byDistance.get(0).lon());
        double secondClosest = distanceMeters(anchorLat, anchorLon, byDistance.get(1).lat(), byDistance.get(1).lon());

        if (secondClosest - closest >= DISTANCE_TIE_MARGIN_METERS) {
            return Optional.of(buildResult(candidateId, name, branchName, List.of(byDistance.get(0))));
        }

        return Optional.of(buildResult(candidateId, name, branchName,
                byDistance.stream().limit(MAX_CONFIRMATION_CANDIDATES).toList()));
    }

    /** 후보 1건이면 확정, 여러 건이면 되묻기 결과를 만든다 - {@link #resolveViaAnchor}의 두 경로가 공유. */
    private Result buildResult(String candidateId, String name, String branchName,
                                List<DataportalStoreClient.StoreResult> matches) {
        if (matches.size() == 1) {
            DataportalStoreClient.StoreResult m = matches.get(0);
            ResolvedPlaceDto place = new ResolvedPlaceDto(candidateId, name, branchName, m.roadAddress(), m.lat(), m.lon());
            return new Result(Decision.RESOLVED, place, null);
        }

        List<ResolvedPlaceDto> options = IntStream.range(0, matches.size())
                .mapToObj(i -> {
                    DataportalStoreClient.StoreResult m = matches.get(i);
                    return new ResolvedPlaceDto(candidateId + "-" + (i + 1), name, branchName, m.roadAddress(), m.lat(), m.lon());
                })
                .toList();
        ConfirmRequestDto confirm = new ConfirmRequestDto("어느 지점이 맞을까요?", options);
        return new Result(Decision.NEEDS_CONFIRMATION, null, confirm);
    }

    /**
     * 브랜드 자체가 확인 안 된 상황({@link #resolveViaVWorldAnchor}가 지점명 텍스트로만 좁힌 경우,
     * 2026-10-09 PR 리뷰로 발견된 버그 수정) 전용 - {@link #buildResult}와 달리 후보가 1건이어도
     * 자동 확정하지 않고 항상 되묻는다(브랜드를 확신할 수 없으므로). 옵션에는 검색했던 브랜드/지점명이
     * 아니라 그 업소 자체의 실제 상호명(bizesNm)·지점명(brchNm)을 넣는다 - 사용자에게 "이 가게가
     * 맞나요?"라고 솔직하게 묻기 위함.
     */
    private Result confirmOnly(String candidateId, List<DataportalStoreClient.StoreResult> stores) {
        List<ResolvedPlaceDto> options = IntStream.range(0, stores.size())
                .mapToObj(i -> new ResolvedPlaceDto(candidateId + "-" + (i + 1), stores.get(i).bizesNm(),
                        stores.get(i).brchNm(), stores.get(i).roadAddress(), stores.get(i).lat(), stores.get(i).lon()))
                .toList();
        return new Result(Decision.NEEDS_CONFIRMATION, null, new ConfirmRequestDto("어느 지점이 맞을까요?", options));
    }

    /** 두 좌표 사이 거리(미터) - Haversine 공식. 반경 몇백m 안에서 순위만 매기면 되므로 정밀도는 충분. */
    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    // haystack=null은 "검색할 텍스트 자체가 없음"이므로 false(불일치)로 처리한다 - LocalDataStoreLookup의
    // brchNm이 항상 null인 경우(2026-10-09, CompositeNearbyStoreLookup 연결 후 실제 재검증 중 NPE로
    // 발견)처럼, 모든 호출부가 null 가능성이 있는 필드를 넘길 수 있어 호출부마다 널 체크를 두는 대신
    // 여기 한 곳에서 안전하게 처리한다.
    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * {@link #containsIgnoreCase}에 {@link #BRAND_NAME_ALIASES}를 더해 브랜드명을 확인한다 - "CU"처럼
     * 공공상가정보 등록 표기(한글 음차)가 후보 이름(로마자)과 글자 자체가 안 겹치는 브랜드 전용.
     */
    private boolean matchesBrand(String bizesNm, String brandName) {
        if (containsIgnoreCase(bizesNm, brandName)) {
            return true;
        }
        String alias = BRAND_NAME_ALIASES.get(brandName);
        return alias != null && containsIgnoreCase(bizesNm, alias);
    }
}
