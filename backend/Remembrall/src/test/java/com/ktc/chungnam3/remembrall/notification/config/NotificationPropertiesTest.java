package com.ktc.chungnam3.remembrall.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void canonicalConstructorBindsDefaultAndCustomAndroidChannel() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(NotificationProperties.class).androidChannelId()).isEqualTo("nearby");
        });
        context.withPropertyValues("notification.android-channel-id=demo-channel").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(NotificationProperties.class).androidChannelId()).isEqualTo("demo-channel");
        });
        context.withPropertyValues("notification.android-channel-id=").run(application ->
                assertThat(application).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    static class PropertiesConfiguration {}
}
