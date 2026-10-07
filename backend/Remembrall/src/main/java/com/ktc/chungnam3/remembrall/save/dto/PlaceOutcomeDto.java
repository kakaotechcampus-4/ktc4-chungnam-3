package com.ktc.chungnam3.remembrall.save.dto;

/**
 * 장소 후보 하나에 대한 판정 결과. {@code PlaceResolver.Result}와 같은 모양(판정값 +
 * resolvedPlace/confirmRequest 중 하나만 채움)을 따르되, candidateId를 최상위 필드로 둬서 NO_PLACE일
 * 때도(resolvedPlace/confirmRequest가 둘 다 null) 어느 후보 얘기인지 알 수 있게 한다. 장소검색/검증
 * API 자체가 실패한 경우를 표현하는 값 2개도 추가했다(추후 ContentAnalysisFailureCode의
 * PLACE_SEARCH_API_ERROR/PLACE_VERIFICATION_API_ERROR에 대응할 자리).
 */
public record PlaceOutcomeDto(
        String candidateId,

        Decision decision,

        ResolvedPlaceDto resolvedPlace,

        ConfirmRequestDto confirmRequest

) {
    public enum Decision {
        RESOLVED,
        NEEDS_CONFIRMATION,
        NO_PLACE,
        /** LocationIQ 검색 호출 자체가 실패함. */
        PLACE_SEARCH_FAILED,
        /** 장소 확정(PlaceResolver.resolve) 중 예외가 발생함. */
        PLACE_VERIFICATION_FAILED
    }
}
