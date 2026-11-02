package com.ktc.chungnam3.remembrall.save.place;

import java.util.List;

/**
 * 지점명 없는 일반 장소(공원·관광지 등)를 이름으로 검색해 공식 명칭과 좌표를 구하는 창구 -
 * {@link TourApiClient}가 실제 구현체. {@link FranchiseAnchorLookup}과 달리 여러 건을 반환하고
 * (키워드 검색이라 동명이인이 섞여 나옴), 응답 자체를 그대로 저장해도 되는 소스다(data.go.kr
 * "이용허락범위 제한 없음" 확인, 2026-10-28 - VWorld처럼 좌표만 쓰고 버리는 앵커 전용이 아님).
 * 테스트에서는 실제 HTTP 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를 준다({@link PlaceLookup}과
 * 같은 이유).
 */
public interface GeneralPlaceAnchorLookup {
    List<TourApiClient.TourApiItem> search(String name);
}
