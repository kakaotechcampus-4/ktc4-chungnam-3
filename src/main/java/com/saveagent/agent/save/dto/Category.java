package com.saveagent.agent.save.dto;

/**
 * 저장 콘텐츠 카테고리.
 * 카테고리와 트리거는 별개다 - 카테고리가 "맛집"이어도 좌표가 없으면 트리거는 생기지 않는다.
 */
public enum Category {
    RESTAURANT,     // 맛집 - 식당·카페·술집
    ATTRACTION,     // 관광지 - 명소·전시·산책로
    EVENT,          // 행사 - 축제·팝업·공연
    ACCOMMODATION,  // 숙소 - 호텔·게스트하우스
    ETC             // 기타 - 위 카테고리에 안 들어가거나 애매한 경우
}
