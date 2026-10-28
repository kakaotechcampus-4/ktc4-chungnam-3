package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JudgePropertiesTest {
    @Test
    void promptOutputIsTheDefault() {
        var properties = bind(Map.of("recall.judge.provider", "google-genai", "recall.judge.model", "fake-model"));

        assertThat(properties.forceStructuredOutput()).isFalse();
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.temperature()).isZero();
        properties.validate();
    }

    @Test
    void forcedStructuredOutputCanBeExplicitlyEnabled() {
        var properties = bind(Map.of("recall.judge.provider", "google-genai", "recall.judge.model", "fake-model",
                "recall.judge.force-structured-output", "true"));

        assertThat(properties.forceStructuredOutput()).isTrue();
        properties.validate();
    }

    private JudgeProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("recall.judge", JudgeProperties.class);
    }
}
