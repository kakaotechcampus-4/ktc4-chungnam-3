package com.ktc.chungnam3.remembrall.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 액세스 토큰입니다."),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다."),
    FCM_TOKEN_CONFLICT(HttpStatus.CONFLICT, "FCM 토큰 등록이 충돌했습니다. 다시 시도해 주세요."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 조회하지 못했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
