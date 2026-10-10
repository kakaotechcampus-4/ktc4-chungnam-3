package com.ktc.chungnam3.remembrall.domain.place;

public enum GeocodingProvider {
    LOCATIONIQ,
    // LocationIQ(해외 API) 커버리지 부족을 보완 - 공공상가정보로 확정된 좌표용(Place.md 담당자 승인,
    // 2026-10-10). DB CHECK 제약도 V14 마이그레이션으로 같이 넓힘.
    DATAPORTAL
}
