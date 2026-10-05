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
     * {@code accept-language=ko}(2026-09-29 추가) 이후 LocationIQ displayName이 한글로 오면서 필요해진
     * 목록 - 시·도 줄임말(맵 키)이 공식 명칭의 접두어인 경우(예: "서울"→"서울특별시")는 키 자체가 이미
     * displayName에 포함되므로 여기 따로 안 넣는다. "도" 6개(충북/충남/전북/전남/경북/경남)만 공식
     * 명칭이 "충청"/"전라"/"경상" 같은 다른 접두어를 써서 줄임말이 부분 문자열로 안 걸리므로 예외로
     * 추가한다. 옛 명칭(전라북도)과 새 명칭(전북특별자치도, 2024년 개칭)을 둘 다 넣어 OSM 데이터
     * 갱신 시점과 무관하게 매칭되게 한다.
     */
    static final Map<String, List<String>> KOREAN_ALIASES = Map.ofEntries(
            Map.entry("충북", List.of("충청북도")),
            Map.entry("충남", List.of("충청남도")),
            Map.entry("전북", List.of("전라북도", "전북특별자치도")),
            Map.entry("전남", List.of("전라남도")),
            Map.entry("경북", List.of("경상북도")),
            Map.entry("경남", List.of("경상남도"))
    );

    /**
     * 시·도 줄임말(키)이 실제 displayName에 어떤 문자열들로 나타날 수 있는지 전부 모아 반환한다 -
     * 키 자체(한글 displayName의 접두어로 대부분 충분), {@link #KOREAN_ALIASES}(예외 6개 도),
     * {@link #ROMANIZED_ALIASES}(과거 응답이나 accept-language 미적용 케이스 대비 하위호환)를 합친다.
     */
    static List<String> displayAliasesFor(String provinceKey) {
        List<String> aliases = new java.util.ArrayList<>();
        aliases.add(provinceKey);
        aliases.addAll(KOREAN_ALIASES.getOrDefault(provinceKey, List.of()));
        aliases.addAll(ROMANIZED_ALIASES.getOrDefault(provinceKey, List.of()));
        return aliases;
    }

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
