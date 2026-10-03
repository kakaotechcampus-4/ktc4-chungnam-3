package com.ktc.chungnam3.remembrall.notification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(@DefaultValue("120") @Min(1) int maxTitleLength,
                                     @DefaultValue("200") @Min(1) int maxBodyLength,
                                     @DefaultValue("") String fcmCredentialsPath) {
}
