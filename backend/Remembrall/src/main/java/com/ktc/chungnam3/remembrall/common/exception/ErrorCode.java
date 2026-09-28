package com.ktc.chungnam3.remembrall.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 액세스 토큰입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 조회하지 못했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
