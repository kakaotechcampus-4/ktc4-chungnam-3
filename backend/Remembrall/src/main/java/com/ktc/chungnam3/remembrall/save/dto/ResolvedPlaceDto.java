package com.ktc.chungnam3.remembrall.save.dto;

import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;

/**
 * geocodingProvider/geocodingPlaceId: 이 좌표를 실제로 준 출처와 그 출처 자체의 식별자 - Place.md의
 * geocoding_provider/geocoding_place_id로 그대로 이어진다(2026-10-10, Place.md 담당자 합의 반영).
 * LocationIQ 기준 후보면 LOCATIONIQ+그 결과의 place_id, 공공상가정보 기준 후보면 DATAPORTAL+bizesId.
 * <p>
 * verificationPlaceId: 카카오 장소 ID(Place.md의 verification_place_id) - PlaceResolver는 카카오를
 * 모르므로 항상 null로 만들고, {@code SavePipelineService}가 확정(RESOLVED) 후보에 한해
 * {@code KakaoPlaceLookup}으로 조회해 채워 넣는다. 못 찾으면 그 후보는 RESOLVED로 내보내지 않는다
 * (Place.md: "결과가 모호하거나 일치하지 않으면 Place를 생성하지 않고 실패 기록").
 */
public record ResolvedPlaceDto(
        String candidateId,

        String name,

        String branchName,

        String address,

        double lat,

        double lng,

        GeocodingProvider geocodingProvider,

        String geocodingPlaceId,

        String verificationPlaceId

) {
}
