package com.saveagent.agent.save.dto;

import java.util.List;

// TODO: 협의 필요 - 수집·요약 담당과 아직 형식이 확정되지 않은 초안(record)이다. (입력 DTO)
/**
 * 수집·요약 담당이 넘겨주는 분석 결과. 저장 에이전트의 입력이다.
 */
public record ContentAnalysis(
        String videoId,
        String title,
        String summary,                // 3줄 요약
        List<String> tags,
        List<PlaceCandidate> places,    // 비어 있을 수 있음
        ScheduleCandidate schedule      // null 가능
) {}
