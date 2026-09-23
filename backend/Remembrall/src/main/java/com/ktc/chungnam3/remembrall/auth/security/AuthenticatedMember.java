package com.ktc.chungnam3.remembrall.auth.security;

import java.util.UUID;

public record AuthenticatedMember(
        UUID memberId,
        UUID deviceId,
        String sessionTokenHash
) {
}
