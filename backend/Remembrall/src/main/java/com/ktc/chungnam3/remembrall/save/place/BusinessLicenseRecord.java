package com.ktc.chungnam3.remembrall.save.place;

/** 지방행정인허가 원본 데이터 한 건을 정규화한 형태. 실제 API 응답 파싱(`BusinessLicenseSource` 구현체)이
 * 이 형태로 변환해 넘긴다. */
public record BusinessLicenseRecord(
        String sourceId,
        String businessName,
        String category,
        String licenseStatus,
        String roadAddress,
        Double latitude,
        Double longitude
) {
}
