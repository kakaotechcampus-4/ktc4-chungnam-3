package com.ktc.chungnam3.remembrall.domain.place;

public enum GeocodingProvider {
    LOCATIONIQ,
    // LocationIQ(해외 API) 커버리지 부족을 보완 - 공공상가정보로 확정된 좌표용(Place.md 담당자 승인,
    // 2026-10-10). DB CHECK 제약도 V14 마이그레이션으로 같이 넓힘.
    DATAPORTAL,
    // 지점명 없는 일반 장소(공원·관광지 등) 전용, 한국관광공사 TourAPI로 확정된 좌표용(2026-10-28,
    // data.go.kr "이용허락범위 제한 없음" 확인 - DATAPORTAL과 같은 근거). DB CHECK 제약도 V16
    // 마이그레이션으로 같이 넓힘.
    TOURAPI
}
