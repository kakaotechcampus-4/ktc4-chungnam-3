package com.saveagent.agent.save.dto;

/**
 * 트리거 종류.
 */
public enum TriggerType {
    LOCATION,   // 위치 트리거 - 좌표가 있을 때
    SCHEDULE    // 기간 트리거 - 기간이 있고 연도가 확인됐을 때
}
