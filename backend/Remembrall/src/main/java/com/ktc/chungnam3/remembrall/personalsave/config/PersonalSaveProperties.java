package com.ktc.chungnam3.remembrall.personalsave.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "personal-save")
public record PersonalSaveProperties(
        @DefaultValue("20") @Min(1) int defaultPageSize,
        @DefaultValue("100") @Min(1) int maxPageSize,
        @DefaultValue("200") @Min(1) int maxSummaryLength,
        @DefaultValue("2") @Min(1) int analysisConcurrency,
        @DefaultValue("100") @Min(0) int analysisQueueCapacity
) {
    @AssertTrue(message = "default-page-size must not exceed max-page-size")
    public boolean isPageSizeValid() {
        return defaultPageSize <= maxPageSize;
    }
}
