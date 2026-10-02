package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import org.springframework.stereotype.Component;

import java.util.List;
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
 * ① 결과 이름에 찾던 이름이 없으면 버림 ② 도로·동네급 결과(class=highway/place)는 버림
 * ③ 시·도가 명백히 다른 결과는 버림 ④ 남은 개수로 확정/되묻기/장소없음 판정, 이때 후보가 1건이어도
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
 */
@Component
public class PlaceResolver {

    private static final int MAX_CONFIRMATION_CANDIDATES = 3;

    // 도로·동네·행정구역급 결과는 업체가 아니므로 확정 후보에서 제외한다.
    private static final Set<String> REJECTED_CLASSES = Set.of("highway", "place");

    // 「을지로 골뱅이→잠실」류 구·동 단위 오매칭을 잡기 위한 반경. 상가업소 등록 주소와 실제 좌표 사이
    // 오차를 감안해 너무 좁게 잡지 않되, 동 단위를 넘어갈 만큼 넓게 잡지도 않는다 - 팀 검토 필요한
    // 기본값(다른 임계값들과 마찬가지로 실측 데이터로 조정 가능).
    private static final int NEARBY_STORE_RADIUS_METERS = 100;

    // 기준점(역·동네 등) 주변에서 프랜차이즈 지점을 찾는 반경 - 위보다 넓게 잡는다. 기준점은 "정확한
    // 지점 좌표"가 아니라 "그 근방을 대표하는 좌표"(예: 역 출구 하나)라 오차가 더 클 수 있어서다.
    private static final int FRANCHISE_ANCHOR_RADIUS_METERS = 300;

    private final NearbyStoreLookup nearbyStoreLookup;
    private final PlaceLookup placeLookup;

    public PlaceResolver(NearbyStoreLookup nearbyStoreLookup, PlaceLookup placeLookup) {
        this.nearbyStoreLookup = nearbyStoreLookup;
        this.placeLookup = placeLookup;
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
                filterByRegion(regionHint, filterByNameAndClass(name, searchResults));

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

    /** 결과 이름에 찾던 이름이 없으면, 그리고 도로·동네급 결과(class=highway/place)면 제외한다. */
    private List<PlaceSearchClient.PlaceSearchResult> filterByNameAndClass(
            String name, List<PlaceSearchClient.PlaceSearchResult> results) {
        return results.stream()
                .filter(r -> containsIgnoreCase(r.displayName(), name))
                .filter(r -> r.osmClass() == null || !REJECTED_CLASSES.contains(r.osmClass().toLowerCase()))
                .toList();
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
        if (regionHint == null || regionHint.isBlank()) {
            return results;
        }

        List<String> aliases = KoreanProvinces.ROMANIZED_ALIASES.keySet().stream()
                .filter(regionHint::contains)
                .flatMap(key -> KoreanProvinces.displayAliasesFor(key).stream())
                .toList();

        if (aliases.isEmpty()) {
            return results;
        }

        return results.stream()
                .filter(r -> aliases.stream().anyMatch(alias -> containsIgnoreCase(r.displayName(), alias)))
                .toList();
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
        return nearby.stream().noneMatch(store -> containsIgnoreCase(store.bizesNm(), name));
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
     * 프랜차이즈 지점 판정(개선③) - regionHint의 세부 지역명으로 기준점을 먼저 찾고, 그 주변을
     * 공공 상가정보로 뒤져서 상호명이 일치하는 실제 업소를 찾는다. 기준점을 못 찾거나, 기준점은
     * 찾았는데 주변에 상호명이 일치하는 업소가 하나도 없으면 빈 값을 반환한다 - 이 경우 호출부가
     * 기존 ①~④ 흐름으로 폴백하므로, 이 메서드는 "찾으면 확정/되묻기, 못 찾으면 모르겠다"만 답한다.
     */
    private Optional<Result> resolveViaAnchor(String candidateId, String name, String branchName, String regionHint) {
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
            nearby = nearbyStoreLookup.searchByRadius(
                    anchor.get().lon(), anchor.get().lat(), FRANCHISE_ANCHOR_RADIUS_METERS);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        List<DataportalStoreClient.StoreResult> brandMatches = nearby.stream()
                .filter(s -> containsIgnoreCase(s.bizesNm(), name))
                .toList();
        if (brandMatches.isEmpty()) {
            return Optional.empty();
        }

        // 지점이 여럿 남으면 지점명(brchNm)으로 한 번 더 좁혀본다 - 안 좁혀지면(brchNm이 비어있거나
        // 표기가 다르면) 그냥 남은 후보 전체로 되묻는다.
        List<DataportalStoreClient.StoreResult> narrowed = brandMatches.stream()
                .filter(s -> containsIgnoreCase(s.brchNm(), branchName))
                .toList();
        List<DataportalStoreClient.StoreResult> finalMatches = narrowed.isEmpty() ? brandMatches : narrowed;

        if (finalMatches.size() == 1) {
            DataportalStoreClient.StoreResult m = finalMatches.get(0);
            ResolvedPlaceDto place = new ResolvedPlaceDto(candidateId, name, branchName, m.roadAddress(), m.lat(), m.lon());
            return Optional.of(new Result(Decision.RESOLVED, place, null));
        }

        List<ResolvedPlaceDto> options = IntStream.range(0, finalMatches.size())
                .mapToObj(i -> {
                    DataportalStoreClient.StoreResult m = finalMatches.get(i);
                    return new ResolvedPlaceDto(candidateId + "-" + (i + 1), name, branchName, m.roadAddress(), m.lat(), m.lon());
                })
                .toList();
        ConfirmRequestDto confirm = new ConfirmRequestDto("어느 지점이 맞을까요?", options);
        return Optional.of(new Result(Decision.NEEDS_CONFIRMATION, null, confirm));
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
