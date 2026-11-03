package com.ktc.chungnam3.remembrall.save.analysis;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Gemini 영상 분석 호출에 타임아웃을 건다(2026-11-02, personal-saves 담당이 요청한 "LocationIQ/공공
 * 데이터처럼 Gemini·YouTube 호출에도 타임아웃을 달아달라"는 요청 반영 - Module A 소유 영역이지만
 * 에스더 승인받아 직접 작업함). Spring AI의 자동 구성 {@code Client} 빈(
 * {@code GoogleGenAiChatAutoConfiguration.googleGenAiClient}, spring-ai-autoconfigure-model-google-genai
 * 2.0.1 디컴파일로 확인)은 타임아웃 설정을 노출하지 않는다. 그 빌드 로직(API 키/Vertex AI 분기)을
 * 그대로 재현하면서 {@code httpOptions.timeout}만 추가한 {@code Client} 빈을 직접 등록해 자동 구성을
 * 대체한다 - 원래 빈 메서드가 {@code @ConditionalOnMissingBean(Client.class)}라 이 빈이 있으면 자동
 * 구성 쪽은 그냥 비활성화된다. 임베딩 쪽({@code GoogleGenAiEmbeddingConnectionAutoConfiguration})은
 * 이 {@code Client} 빈을 공유하지 않고 자체 커넥션을 만들어서 영향 없음(디컴파일로 확인).
 */
@Configuration(proxyBeanMethods = false)
public class GoogleGenAiClientConfig {

    @Bean
    public Client googleGenAiClient(
            GoogleGenAiConnectionProperties properties,
            @Value("${gemini.chat-timeout-ms:60000}") int timeoutMs) throws IOException {
        Client.Builder builder = Client.builder();
        boolean hasApiKey = StringUtils.hasText(properties.getApiKey());
        boolean hasVertexConfig = StringUtils.hasText(properties.getProjectId())
                && StringUtils.hasText(properties.getLocation());

        if (properties.isVertexAi()) {
            if (!hasVertexConfig) {
                throw new IllegalStateException(
                        "Vertex AI mode requires both 'project-id' and 'location' to be configured.");
            }
            configureVertexAi(builder, properties);
        } else if (hasApiKey) {
            builder.apiKey(properties.getApiKey());
        } else if (hasVertexConfig) {
            configureVertexAi(builder, properties);
        } else {
            throw new IllegalStateException(
                    "Incomplete Google GenAI configuration: Provide 'api-key' for Gemini API or 'project-id' and 'location' for Vertex AI.");
        }

        builder.httpOptions(HttpOptions.builder().timeout(timeoutMs).build());
        return builder.build();
    }

    private void configureVertexAi(Client.Builder builder, GoogleGenAiConnectionProperties properties) throws IOException {
        Assert.hasText(properties.getProjectId(), "Google GenAI project-id must be set for Vertex AI mode!");
        Assert.hasText(properties.getLocation(), "Google GenAI location must be set for Vertex AI mode!");
        builder.project(properties.getProjectId());
        builder.location(properties.getLocation());
        builder.vertexAI(true);
        if (properties.getCredentialsUri() != null) {
            try (InputStream in = properties.getCredentialsUri().getInputStream()) {
                builder.credentials(GoogleCredentials.fromStream(in));
            }
        }
    }
}
