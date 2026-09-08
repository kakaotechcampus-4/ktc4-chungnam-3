package com.saveagent.agent.save.dto;

import java.util.List;

// TODO: 협의 필요 - TriggerSpec을 꺼내기 에이전트와 공유 클래스로 둘지 확정되면 이 출력 구조도 같이 조정될 수 있다.
/**
 * 저장 에이전트의 출력. 상위 저장 로직에 반환된다 (DB 저장은 이 에이전트의 책임이 아니다).
 */
public record AgentResult(
        Status status,
        Category category,
        List<TriggerSpec> triggers, // 비어 있을 수 있음 - 이것은 실패가 아니라 정상 경로다
        double confidence,
        ConfirmRequest confirm      // status == NEEDS_CONFIRM 일 때만 값이 있고, 그 외에는 null
) {}
