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
 * 지점명이 다르면 확정하지 않고 되묻는다 (검색어 자체엔 지점명을 안 넣으므로 - PlaceSearchClient 참고 -
 * 지점 특정은 여기서 한다).
 * <p>
 * TODO: 시·도 단위 지역 교차검증만 있어서, 같은 시·도 안에서 구·동 단위까지 틀린 동명이인(예: 송파의
 * 「을지로 골뱅이」)은 여전히 못 잡는다. 공공 상가정보 연동(개선 ②, 별도 작업)이 나와야 근본 해결됨.
 */
@Component
public class PlaceResolver {

    private static final int MAX_CONFIRMATION_CANDIDATES = 3;

    // 도로·동네·행정구역급 결과는 업체가 아니므로 확정 후보에서 제외한다.
    private static final Set<String> REJECTED_CLASSES = Set.of("highway", "place");

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

            if (branchMatchesOrUnspecified) {
                ResolvedPlaceDto place = new ResolvedPlaceDto(
                        candidateId, name, branchName, match.displayName(), match.lat(), match.lon());
                return new Result(Decision.RESOLVED, place, null);
            }

            // 이름은 맞는데 찾던 지점인지 확신할 수 없으니 확정하지 않고 되묻는다.
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
     */
    private List<PlaceSearchClient.PlaceSearchResult> filterByRegion(
            String regionHint, List<PlaceSearchClient.PlaceSearchResult> results) {
        if (regionHint == null || regionHint.isBlank()) {
            return results;
        }

        List<String> aliases = KoreanProvinces.ROMANIZED_ALIASES.entrySet().stream()
                .filter(entry -> regionHint.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        if (aliases.isEmpty()) {
            return results;
        }

        return results.stream()
                .filter(r -> aliases.stream().anyMatch(alias -> containsIgnoreCase(r.displayName(), alias)))
                .toList();
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
