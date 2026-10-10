package com.ktc.chungnam3.remembrall.save.place;

import java.util.Optional;

/**
 * 확정된 좌표가 카카오 데이터베이스상 어떤 장소 ID인지 찾는 창구. {@link KakaoPlaceClient}가 실제
 * 구현체고, 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다
 * ({@link PlaceLookup}/{@link NearbyStoreLookup}과 같은 이유 - 팀 관행: 별도 모킹 프레임워크 안 씀).
 */
public interface KakaoPlaceLookup {
    Optional<String> findPlaceId(String name, String branchName, double lat, double lon);
}
