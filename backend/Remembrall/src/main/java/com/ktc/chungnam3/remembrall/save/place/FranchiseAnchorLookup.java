package com.ktc.chungnam3.remembrall.save.place;

import java.util.Optional;

/**
 * 브랜드(+지점명)로 프랜차이즈 지점의 정확한 좌표를 구하는 창구 - 공공 상가정보 반경검색의
 * 기준점 보정 전용이다({@link VWorldSearchClient}가 실제 구현체). 응답은 저장하지 않고 그 자리에서
 * 중심점으로만 쓰고 버린다(2026-10-06 확정 - VWorld 이용약관 제19조 6항 저장 금지 조항 때문).
 * 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다({@link PlaceLookup}과
 * 같은 이유).
 */
public interface FranchiseAnchorLookup {
    Optional<PlaceSearchClient.PlaceSearchResult> findAnchor(String query);
}
