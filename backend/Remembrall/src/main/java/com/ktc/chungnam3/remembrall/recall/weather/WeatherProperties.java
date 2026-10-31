package com.ktc.chungnam3.remembrall.recall.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("recall.weather")
public record WeatherProperties(
        @DefaultValue("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0") String baseUrl,
        @DefaultValue("") String serviceKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("6") int forecastHours,
        @DefaultValue("512") int maxCacheEntries) {
    public WeatherProperties {
        if (baseUrl == null || baseUrl.isBlank() || connectTimeout == null || connectTimeout.isNegative()
                || connectTimeout.isZero() || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()
                || forecastHours < 1 || forecastHours > 24 || maxCacheEntries < 1) {
            throw new IllegalArgumentException("Invalid recall weather settings");
        }
    }
}
