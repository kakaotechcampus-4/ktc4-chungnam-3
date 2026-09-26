package com.ktc.chungnam3.remembrall.auth.kakao;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoUserClient {

    private static final String BASE_URL = "https://kapi.kakao.com";

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;

    public KakaoUserClient(RestClient.Builder restClientBuilder, KakaoProperties kakaoProperties) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.kakaoProperties = kakaoProperties;
    }

    public String getProviderUserId(String kakaoAccessToken) {
        try {
            KakaoAccessTokenInfo tokenInfo = restClient.get()
                    .uri("/v1/user/access_token_info")
                    .headers(headers -> headers.setBearerAuth(kakaoAccessToken))
                    .retrieve()
                    .body(KakaoAccessTokenInfo.class);

            if (tokenInfo == null || tokenInfo.id() == null || tokenInfo.appId() == null) {
                throw new ApiException(ErrorCode.KAKAO_API_ERROR);
            }
            if (!kakaoProperties.appId().equals(tokenInfo.appId())) {
                throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN);
            }
            return tokenInfo.id().toString();
        } catch (RestClientResponseException exception) {
            HttpStatusCode statusCode = exception.getStatusCode();
            if (statusCode.is4xxClientError()) {
                throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN, exception);
            }
            throw new ApiException(ErrorCode.KAKAO_API_ERROR, exception);
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.KAKAO_API_ERROR, exception);
        }
    }
}
