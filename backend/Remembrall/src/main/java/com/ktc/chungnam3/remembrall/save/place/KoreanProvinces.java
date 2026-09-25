package com.ktc.chungnam3.remembrall.save.place;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 대한민국 시·도(광역시·도) 17개 - 이름과, LocationIQ(OSM/Nominatim)가 실제로 쓰는 로마자 표기
 * 변형까지 포함한다 (예: 대전 -> Daejon/Daejeon). {@link PlaceSearchClient}(검색어에서 시·도만
 * 뽑아 쓰기)와 {@link PlaceResolver}(지역 교차검증)가 같은 목록을 공유한다.
 */
final class KoreanProvinces {

    static final Map<String, List<String>> ROMANIZED_ALIASES = Map.ofEntries(
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

    /**
     * regionHint 문자열 안에서 17개 시·도 이름 중 하나를 찾아 반환한다. regionHint가 "서울 을지로"처럼
     * 구·동 단위까지 포함해도, 검색어에는 "을지로" 같은(지도 데이터에 잘 없을 수 있는) 단어를 넣지 않고
     * "서울"만 넣기 위해 쓴다. 못 찾으면 빈 값 - 이 경우 호출부가 지역 없이 검색하도록 한다.
     */
    static Optional<String> extract(String regionHint) {
        if (regionHint == null || regionHint.isBlank()) {
            return Optional.empty();
        }
        return ROMANIZED_ALIASES.keySet().stream()
                .filter(regionHint::contains)
                .findFirst();
    }

    private KoreanProvinces() {
    }
}
