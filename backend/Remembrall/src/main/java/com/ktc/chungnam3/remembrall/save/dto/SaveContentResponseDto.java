package com.ktc.chungnam3.remembrall.save.dto;

import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/contents} 응답. alreadyProcessed가 true면 이 콘텐츠는 이미 다른 요청이 분석을
 * 끝냈거나 진행 중이라는 뜻이고(분석은 콘텐츠당 한 번만), 그 경우 status 이하 필드는 전부 null/빈 값이다
 * - 분석 결과 자체를 다시 조회하는 건 이번 범위 밖(ContentAnalysisResultService 쪽 조회 API 필요).
 */
public record SaveContentResponseDto(

        UUID contentId,

        boolean alreadyProcessed,

        ExtractionStatus status,

        String summary,

        List<PlaceOutcomeDto> places,

        String failureMessage

) {
    public static SaveContentResponseDto alreadyProcessed(UUID contentId) {
        return new SaveContentResponseDto(contentId, true, null, null, List.of(), null);
    }

    public static SaveContentResponseDto from(UUID contentId, SavePipelineResultDto result) {
        return new SaveContentResponseDto(
                contentId, false, result.status(), result.summary(), result.places(), result.failureMessage());
    }
}
