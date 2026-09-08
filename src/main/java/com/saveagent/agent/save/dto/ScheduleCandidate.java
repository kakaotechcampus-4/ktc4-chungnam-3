package com.saveagent.agent.save.dto;

import java.time.LocalDate;

// TODO: 협의 필요 - 수집·요약 담당과 아직 형식이 확정되지 않은 초안(record)이다.
/**
 * 콘텐츠에서 추출된 기간(행사·일정) 후보.
 */
public record ScheduleCandidate(
        String eventName,
        LocalDate startDate,
        LocalDate endDate,
        boolean yearConfirmed // false면 트리거를 생성하지 않는다 (작년 축제 오발동 방지)
) {}
