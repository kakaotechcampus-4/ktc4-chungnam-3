package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * LocationIQ 검색 결과를 걸러서 장소를 확정할지, 사용자에게 되물을지, 확정할 수 없다고 볼지
 * 판단한다 (저장 파이프라인.md 3장 "판정" 기준, 도구 검토 문서 2026-09-23 "개선 ①" 반영).
 * <p>
 * 판정 순서: ① 결과 이름에 찾던 이름이 없으면 버림 ② 도로·동네급 결과(class=highway/place)는 버림
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

    private final NearbyStoreLookup nearbyStoreLookup;

    public PlaceResolver(NearbyStoreLookup nearbyStoreLookup) {
        this.nearbyStoreLookup = nearbyStoreLookup;
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
            return false;
        }

        if (nearby.isEmpty()) {
            return false;
        }
        return nearby.stream().noneMatch(store -> containsIgnoreCase(store.bizesNm(), name));
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
