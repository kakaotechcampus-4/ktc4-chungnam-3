package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * LocationIQ 검색 결과 개수에 따라 장소를 확정할지, 사용자에게 되물을지, 확정할 수 없다고
 * 볼지 판단한다 (저장 파이프라인.md 3장 "판정" 기준 — 1건 확정, 2~3건 되묻기, 그 외 NO_PLACE).
 * <p>
 * TODO: 후보가 1건일 때 이게 정말 신뢰할 만한 매치인지 추가로 검증하는 로직은 지역(시·도) 단위까지만
 * 있음 — "을지로 골뱅이 골목"이 "잠실"로 잘못 나온 사례처럼 같은 시·도 안에서 구·동 단위가 틀린
 * 경우는 이 필터로 못 잡는다 (LocationIQ 테스트 결과, CLAUDE.md 참고). 이건 카카오/네이버 하이브리드
 * 결정이 나야 근본적으로 해결됨.
 */
@Component
public class PlaceResolver {

    private static final int MAX_CONFIRMATION_CANDIDATES = 3;

    // LocationIQ(OSM/Nominatim)가 실제로 쓰는 로마자 표기 변형까지 포함 (예: 대전 -> Daejon/Daejeon).
    // 시·도 단위까지만 다룬다 - 구·동 단위 표기는 변형이 너무 많아 이 표로 감당 못 함.
    private static final Map<String, List<String>> REGION_ALIASES = Map.ofEntries(
            Map.entry("서울", List.of("Seoul")),
            Map.entry("부산", List.of("Busan")),
            Map.entry("대구", List.of("Daegu")),
            Map.entry("인천", List.of("Incheon")),
            Map.entry("광주", List.of("Gwangju")),
            Map.entry("대전", List.of("Daejon", "Daejeon")),
            Map.entry("울산", List.of("Ulsan")),
            Map.entry("세종", List.of("Sejong")),
            Map.entry("경기", List.of("Gyeonggi", "Kyeonggi", "Kyeongki")),
            Map.entry("강원", List.of("Gangwon")),
            Map.entry("충북", List.of("Chungbuk", "Chungcheongbuk")),
            Map.entry("충남", List.of("Chungnam", "Chungcheongnam")),
            Map.entry("전북", List.of("Jeonbuk", "Jeollabuk")),
            Map.entry("전남", List.of("Jeonnam", "Jeollanam")),
            Map.entry("경북", List.of("Gyeongbuk", "Gyeongsangbuk")),
            Map.entry("경남", List.of("Gyeongnam", "Gyeongsangnam")),
            Map.entry("제주", List.of("Jeju"))
    );

    public enum Decision {
        RESOLVED,
        NEEDS_CONFIRMATION,
        NO_PLACE
    }

    public record Result(Decision decision, ResolvedPlaceDto resolvedPlace, ConfirmRequestDto confirmRequest) {
    }

    public Result resolve(String candidateId, String name, String branchName, String regionHint,
                           List<PlaceSearchClient.PlaceSearchResult> searchResults) {
        List<PlaceSearchClient.PlaceSearchResult> filtered = filterByRegion(regionHint, searchResults);

        if (filtered.isEmpty() || filtered.size() > MAX_CONFIRMATION_CANDIDATES) {
            return new Result(Decision.NO_PLACE, null, null);
        }

        if (filtered.size() == 1) {
            PlaceSearchClient.PlaceSearchResult match = filtered.get(0);
            ResolvedPlaceDto place = new ResolvedPlaceDto(
                    candidateId, name, branchName, match.displayName(), match.lat(), match.lon());
            return new Result(Decision.RESOLVED, place, null);
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
     * regionHint(시·도 단위)와 검색 결과 주소가 겹치는지 확인해 명백히 다른 시·도인 결과를 제외한다.
     * regionHint가 없거나 매핑표에 없는(구·동 단위 등) 지역명이면 교차검증을 건너뛰고 그대로 반환한다 -
     * 모르는 걸 걸러내려다 오히려 잘못 걸러내는 것보다 안전하다.
     */
    private List<PlaceSearchClient.PlaceSearchResult> filterByRegion(
            String regionHint, List<PlaceSearchClient.PlaceSearchResult> results) {
        if (regionHint == null || regionHint.isBlank()) {
            return results;
        }

        List<String> aliases = REGION_ALIASES.entrySet().stream()
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
