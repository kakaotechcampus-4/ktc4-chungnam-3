package com.ktc.chungnam3.remembrall.auth.kakao;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        @NotNull Long appId
) {
}
