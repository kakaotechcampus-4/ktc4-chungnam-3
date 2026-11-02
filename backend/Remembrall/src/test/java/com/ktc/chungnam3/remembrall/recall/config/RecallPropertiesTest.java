package com.ktc.chungnam3.remembrall.recall.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RecallPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToTwoHourMemberCooldownAndThirtyMinuteCourseLimitWithoutYaml() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            var properties = application.getBean(RecallProperties.class);
            assertThat(properties.memberCooldown()).isEqualTo(Duration.ofHours(2));
            assertThat(properties.maxCourseWalkingMinutes()).isEqualTo(30);
            assertThat(properties.maxEventRetries()).isEqualTo(1);
        });
    }

    @Test
    void explicitCooldownAndCourseLimitOverrideDefaults() {
        context.withPropertyValues("recall.member-cooldown=10m", "recall.max-course-walking-minutes=45", "recall.max-event-retries=3")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var properties = application.getBean(RecallProperties.class);
                    assertThat(properties.memberCooldown()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.maxCourseWalkingMinutes()).isEqualTo(45);
                    assertThat(properties.maxEventRetries()).isEqualTo(3);
                });
    }

    @Test
    void retriesCanBeDisabledButCannotBeNegative() {
        context.withPropertyValues("recall.max-event-retries=0").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(RecallProperties.class).maxEventRetries()).isZero();
        });
        context.withPropertyValues("recall.max-event-retries=-1").run(application -> assertThat(application).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecallProperties.class)
    static class PropertiesConfiguration {}
}
