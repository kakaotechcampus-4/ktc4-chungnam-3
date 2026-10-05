package com.ktc.chungnam3.remembrall.notification.push;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebasePushSenderTest {
    @Test
    void adminSdkUsesHttpV1AndSerializesAllRequiredFields() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        try (var sender = sender(200, "{\"name\":\"projects/test/messages/123\"}", body)) {
            Instant now = Instant.parse("2026-10-02T00:00:00Z");
            UUID id = UUID.randomUUID();
            assertThat(sender.send(PushMessage.notification("fcm-token", "title", "body", id, now)))
                    .isEqualTo("projects/test/messages/123");
            var message = JsonMapper.builder().build().readTree(body.get()).get("message");
            assertThat(message.get("token").asString()).isEqualTo("fcm-token");
            assertThat(message.get("notification").get("title").asString()).isEqualTo("title");
            assertThat(message.get("notification").get("body").asString()).isEqualTo("body");
            assertThat(message.get("data").size()).isOne();
            assertThat(message.get("data").get("notificationId").asString()).isEqualTo(id.toString());
            assertThat(message.get("android").get("ttl").asString()).isEqualTo("1800s");
            assertThat(message.get("android").get("notification").get("tag").asString()).isEqualTo(id.toString());
            assertThat(message.get("apns").get("headers").get("apns-expiration").asString())
                    .isEqualTo(Long.toString(now.plusSeconds(1800).getEpochSecond()));
            assertThat(message.get("apns").get("headers").get("apns-collapse-id").asString()).isEqualTo(id.toString());
        }
    }

    @ParameterizedTest
    @CsvSource({"404, NOT_FOUND, UNREGISTERED", "400, INVALID_ARGUMENT, INVALID_ARGUMENT"})
    void translatesActualFcmErrors(int status, String httpCode, String fcmCode) throws Exception {
        String response = """
                {"error":{"code":%d,"status":"%s","message":"FCM test failure","details":[
                  {"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"%s"}]}}
                """.formatted(status, httpCode, fcmCode);
        try (var sender = sender(status, response, new AtomicReference<>())) {
            assertThatThrownBy(() -> sender.send(PushMessage.notification("fcm-token", "title", "body",
                    UUID.randomUUID(), Instant.now())))
                    .isInstanceOfSatisfying(PushSendException.class,
                            exception -> assertThat(exception.failureCode()).isEqualTo(fcmCode));
        }
    }

    @Test
    void absentCredentialsUseExplicitFailureSender() {
        var configuration = new com.ktc.chungnam3.remembrall.notification.config.NotificationConfiguration();
        assertThatThrownBy(() -> configuration.pushSender(
                        new com.ktc.chungnam3.remembrall.notification.config.NotificationProperties(120, 200, ""))
                .send(PushMessage.notification("token", "title", "body", UUID.randomUUID(), Instant.now())))
                .isInstanceOfSatisfying(PushSendException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("FCM_NOT_CONFIGURED"));
    }

    private FirebasePushSender sender(int status, String response, AtomicReference<String> body) {
        var transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                assertThat(method).isEqualTo("POST");
                assertThat(url).isEqualTo("https://fcm.googleapis.com/v1/projects/test-project/messages:send");
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        body.set(getContentAsString());
                        return new MockLowLevelHttpResponse().setStatusCode(status)
                                .setContentType("application/json; charset=utf-8").setContent(response);
                    }
                };
            }
        };
        var credentials = GoogleCredentials.create(new AccessToken("test-access-token",
                Date.from(Instant.now().plusSeconds(3600))));
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials)
                .setProjectId("test-project").setHttpTransport(transport).build();
        return new FirebasePushSender(FirebaseApp.initializeApp(options, "test-" + UUID.randomUUID()));
    }
}
