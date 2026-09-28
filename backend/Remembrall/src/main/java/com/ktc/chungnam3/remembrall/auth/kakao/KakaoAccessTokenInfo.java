package com.ktc.chungnam3.remembrall.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoAccessTokenInfo(
        Long id,
        @JsonProperty("app_id") Long appId
) {
}
