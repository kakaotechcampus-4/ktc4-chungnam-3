package com.saveagent.agent.save.dto;

// TODO: 협의 필요 - 수집·요약 담당과 아직 형식이 확정되지 않은 초안(record)이다.
/**
 * 콘텐츠에서 추출된 장소 후보.
 */
public record PlaceCandidate(
        String name,        // "○○로스터리"
        String rawText,     // "대흥동 ○○로스터리"
        String regionText,  // "대전 중구 대흥동"
        Double lat,         // null 가능 (좌표 조회 실패 시) - 절대 추측해서 채우지 않는다
        Double lng
) {}
