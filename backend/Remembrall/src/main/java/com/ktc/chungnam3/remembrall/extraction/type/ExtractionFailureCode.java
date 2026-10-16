package com.ktc.chungnam3.remembrall.extraction.type;

public enum ExtractionFailureCode {
    /** 원본 접근 불가를 확인함 */
    VIDEO_UNAVAILABLE,
    /** YouTube 또는 Gemini 호출 실패 */
    EXTRACTION_API_ERROR,
    /** 응답 변환 실패 */
    EXTRACTION_RESULT_ERROR
}
