package com.ktc.chungnam3.remembrall.save.place;

import java.util.List;

/**
 * 좌표 반경 내 실제 상가업소 존재 여부를 조회하는 창구. {@link DataportalStoreClient}가 실제 구현체고,
 * 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다(팀 관행: 별도
 * 모킹 프레임워크 안 씀).
 */
public interface NearbyStoreLookup {
    List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters);

    /**
     * 업종 대분류 코드(예: "I2"=음식, "G2"=소매)로 서버 쪽에서 먼저 좁혀서 조회한다(2026-09-30 추가).
     * 강남역처럼 상권이 밀집한 곳은 반경 안에 상가업소가 수천 건이라 업종 필터 없이는 원하는 브랜드가
     * 응답에 아예 안 잡힐 수 있다(실측 확인함). 기본 구현은 필터 없이 3개 인자 메서드로 위임하므로,
     * 기존 테스트(람다 구현)는 그대로 동작한다 - 실제 카테고리 필터링은 {@link DataportalStoreClient}만
     * 오버라이드해서 제공한다.
     */
    default List<DataportalStoreClient.StoreResult> searchByRadius(
            double lon, double lat, int radiusMeters, List<String> industryLargeCategoryCodes) {
        return searchByRadius(lon, lat, radiusMeters);
    }
}
