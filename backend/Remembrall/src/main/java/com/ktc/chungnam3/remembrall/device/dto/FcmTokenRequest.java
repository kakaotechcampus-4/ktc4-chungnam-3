package com.ktc.chungnam3.remembrall.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenRequest(
        @NotBlank
        @Size(max = 512)
        String fcmToken
) {
}
