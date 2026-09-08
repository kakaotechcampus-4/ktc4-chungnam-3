package com.saveagent.agent.save.dto;

import java.util.Map;

// TODO: 협의 필요 - 꺼내기 에이전트가 그대로 읽어야 하는 구조라 공유 클래스로 뺄지 아직 협의 중이다.
/**
 * 트리거 하나의 명세. 꺼내기 에이전트가 이 값을 읽어 실제 알림 등록에 사용할 예정이다.
 */
public record TriggerSpec(
        TriggerType type,
        // 종류마다 담기는 내용이 다르다.
        // LOCATION: { lat, lng, radiusM }
        // SCHEDULE: { startDate, endDate, notifyBeforeDays }
        Map<String, Object> params
) {}
