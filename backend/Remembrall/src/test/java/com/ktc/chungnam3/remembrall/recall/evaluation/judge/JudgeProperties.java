package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

public record JudgeProperties(String provider, String model, @DefaultValue("60s") Duration timeout,
                              String openaiBaseUrl, String openaiApiKey,
                              @DefaultValue("0") Double temperature,
                              @DefaultValue("false") boolean forceStructuredOutput) {
    public void validate() {
        if (provider == null || (!provider.equals("google-genai") && !provider.equals("openai"))) {
            throw new IllegalArgumentException("Specify recall.judge.provider=google-genai or openai");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Specify recall.judge.model; no default Judge model is configured");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || temperature == null || !Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Judge timeout must be positive and temperature between 0 and 2");
        }
        if (provider.equals("openai") && (openaiBaseUrl == null || openaiBaseUrl.isBlank()
                || openaiApiKey == null || openaiApiKey.isBlank())) {
            throw new IllegalArgumentException("Judge OpenAI-compatible base URL and API key are required");
        }
    }
}
