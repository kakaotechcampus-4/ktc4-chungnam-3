package com.ktc.chungnam3.remembrall.recall.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "recall.agent")
public record RecallAgentProperties(
        @DefaultValue("STUB") @NotNull Mode mode,
        @DefaultValue("GOOGLE_GENAI") @NotNull Provider provider,
        @DefaultValue("gemini-3.6-flash") @NotBlank String model,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("https://api.openai.com/v1") @NotBlank String openaiBaseUrl,
        @DefaultValue("") String openaiApiKey
) {
    public RecallAgentProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("Recall model timeout must be positive");
        }
    }

    public enum Mode { STUB, LLM }
    public enum Provider { GOOGLE_GENAI, OPENAI }
}
