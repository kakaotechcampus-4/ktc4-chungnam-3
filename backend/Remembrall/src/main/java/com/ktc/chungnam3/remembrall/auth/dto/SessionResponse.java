package com.ktc.chungnam3.remembrall.auth.dto;

import java.time.Instant;

public record SessionResponse(
        String sessionToken,
        Instant sessionExpiresAt
) {
}
