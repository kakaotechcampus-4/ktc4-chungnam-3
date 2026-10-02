package com.ktc.chungnam3.remembrall.notification.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.ktc.chungnam3.remembrall.notification.push.FirebasePushSender;
import com.ktc.chungnam3.remembrall.notification.push.PushSender;
import com.ktc.chungnam3.remembrall.notification.push.UnavailablePushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender pushSender(NotificationProperties properties) throws IOException {
        if (properties.fcmCredentialsPath().isBlank()) {
            return new UnavailablePushSender();
        }
        try (InputStream stream = Files.newInputStream(Path.of(properties.fcmCredentialsPath()))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(stream)).build();
            return new FirebasePushSender(FirebaseApp.initializeApp(options, "notification-" + UUID.randomUUID()));
        }
    }
}
