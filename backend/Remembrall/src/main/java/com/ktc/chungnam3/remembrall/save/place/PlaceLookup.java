package com.ktc.chungnam3.remembrall.save.place;

import java.util.List;

/**
 * 이름+지역으로 장소 후보를 검색하는 창구. {@link PlaceSearchClient}가 실제 구현체(LocationIQ)고,
 * 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다({@link NearbyStoreLookup}과
 * 같은 이유 - 팀 관행: 별도 모킹 프레임워크 안 씀).
 */
public interface PlaceLookup {
    List<PlaceSearchClient.PlaceSearchResult> search(String name, String branchName, String regionHint);
}
