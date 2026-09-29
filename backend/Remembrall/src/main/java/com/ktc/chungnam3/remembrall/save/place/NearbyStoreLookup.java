package com.ktc.chungnam3.remembrall.save.place;

import java.util.List;

/**
 * 좌표 반경 내 실제 상가업소 존재 여부를 조회하는 창구. {@link DataportalStoreClient}가 실제 구현체고,
 * 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다(팀 관행: 별도
 * 모킹 프레임워크 안 씀).
 */
public interface NearbyStoreLookup {
    List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters);
}
