package com.ktc.chungnam3.remembrall.save.dto;

import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;

import java.util.List;

/**
 * SavePipelineService 최종 반환. 영상 하나에 장소 후보가 여럿 나올 수 있어(예: "여행지 Top 5" 영상)
 * 과거의 단수 place/confirm 필드를 복수(List&lt;PlaceOutcomeDto&gt;)로 바꿨다 - 후보마다 독립적으로
 * RESOLVED/NEEDS_CONFIRMATION/NO_PLACE/검색실패가 갈릴 수 있어 하나의 전체 status로 뭉뚱그릴 수 없다.
 * <p>
 * status는 콘텐츠 단위 상태다(장소 단위 상태는 PlaceOutcomeDto.decision이 따로 책임진다):
 * <ul>
 *   <li>FAILED: 영상 분석(VideoContentAnalyzer) 자체가 실패함 - summary/places 없음</li>
 *   <li>PARTIAL: 영상 분석은 성공했지만 장소 후보 중 하나 이상에서 검색/검증 API가 실패함</li>
 *   <li>SUCCESS: 영상 분석 성공 - 장소가 전부 없어도(NO_PLACE)/되묻기가 끼어도 SUCCESS다
 *       ("저장담당 요구 사항.md"의 최종 반환 계약 기준: 장소없음·되묻기는 SUCCESS에 포함되는
 *       정상 케이스)</li>
 * </ul>
 * 이미 extraction 패키지가 Module A/B 경계 타입으로 쓰는 {@link ExtractionStatus}(SUCCESS/PARTIAL/
 * FAILED)를 그대로 재사용한다 - 의미가 완전히 겹치는 3값 enum을 새로 또 만들 이유가 없다.
 */
public record SavePipelineResultDto(
        ExtractionStatus status,

        String summary,

        List<PlaceOutcomeDto> places,

        String failureMessage

) {
}
