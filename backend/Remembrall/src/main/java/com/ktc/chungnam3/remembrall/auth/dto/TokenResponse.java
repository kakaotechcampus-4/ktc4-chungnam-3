package com.ktc.chungnam3.remembrall.auth.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
) {
}
