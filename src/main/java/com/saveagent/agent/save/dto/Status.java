package com.saveagent.agent.save.dto;

/**
 * 저장 에이전트 처리 상태.
 */
public enum Status {
    DONE,           // 분류·트리거 설계 완료
    NEEDS_CONFIRM   // 확신도가 낮아 사용자에게 되물어야 함 (에이전트는 대기하지 않고 여기서 종료)
}
