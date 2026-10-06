package com.ktc.chungnam3.remembrall.extraction.type;

/**
 * 원본 영상에 실제로 접근 가능한지 여부. UNAVAILABLE은 유튜브가 "영상을 찾을 수 없음(비공개/삭제됨)"을
 * 명확히 확인해준 경우에만 쓴다 - 다른 이유(네트워크 오류 등)로 메타데이터 조회가 실패했을 때는
 * UNKNOWN으로 둔다 (저장담당 요구 사항.md: "실패했다는 이유만으로 UNAVAILABLE로 판단하지 않는다").
 */
public enum SourceStatus {
    UNKNOWN, AVAILABLE, UNAVAILABLE
}
