package com.ktc.chungnam3.remembrall.save.dto;

/**
 * 되묻기 후보에 대한 사용자 답변 - {@code selectedOptionId}는 {@code ConfirmRequestDto.candidates}
 * 안의 {@code ResolvedPlaceDto.candidateId}(옵션별로 채번된 값, 예: "p1-1") 중 하나다. null이면
 * "후보 중에 없음" - 이 경우 Place를 만들지 않고 후보를 ANSWERED로만 닫는다.
 */
public record ConfirmAnswerRequestDto(
        String selectedOptionId
) {
}
