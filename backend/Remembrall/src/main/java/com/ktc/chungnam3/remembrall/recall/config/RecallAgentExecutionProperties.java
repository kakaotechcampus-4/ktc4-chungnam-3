package com.ktc.chungnam3.remembrall.recall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "recall.agent")
public record RecallAgentExecutionProperties(
        @DefaultValue("true") boolean toolsEnabled,
        @DefaultValue("6") int maxToolCalls,
        @DefaultValue("90s") Duration executionTimeout,
        @DefaultValue("24000") int maxToolResponseBytes
) {
    public RecallAgentExecutionProperties {
        if (maxToolCalls < 1 || executionTimeout == null || executionTimeout.isNegative()
                || executionTimeout.isZero() || executionTimeout.toNanos() < 1 || maxToolResponseBytes < 128) {
            throw new IllegalArgumentException("Invalid recall agent execution limits");
        }
    }

    public static RecallAgentExecutionProperties defaults() {
        return new RecallAgentExecutionProperties(true, 6, Duration.ofSeconds(90), 24000);
    }
}
