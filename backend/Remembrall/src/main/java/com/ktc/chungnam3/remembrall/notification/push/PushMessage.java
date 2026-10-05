package com.ktc.chungnam3.remembrall.notification.push;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PushMessage(String token, String title, String body, Map<String, String> data,
                          Duration androidTtl, String androidTag, Map<String, String> apnsHeaders) {
    public static PushMessage notification(String token, String title, String body, UUID id, Instant now) {
        String notificationId = id.toString();
        return new PushMessage(token, title, body, Map.of("notificationId", notificationId),
                Duration.ofMinutes(30), notificationId,
                Map.of("apns-expiration", Long.toString(now.plus(Duration.ofMinutes(30)).getEpochSecond()),
                        "apns-collapse-id", notificationId));
    }
}
