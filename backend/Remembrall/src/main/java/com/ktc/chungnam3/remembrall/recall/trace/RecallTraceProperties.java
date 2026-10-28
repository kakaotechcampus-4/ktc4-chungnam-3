package com.ktc.chungnam3.remembrall.recall.trace;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "recall.trace")
public record RecallTraceProperties(
        @DefaultValue("65536") @Min(256) int maxDetailBytes,
        @DefaultValue("1000") @Min(1) @Max(4000) int maxErrorLength,
        @DefaultValue("false") boolean includeAgentInput
) {
    public RecallTraceProperties {
        if (maxDetailBytes < 256 || maxErrorLength < 1 || maxErrorLength > 4000) {
            throw new IllegalArgumentException("Invalid recall trace size limits");
        }
    }
}
