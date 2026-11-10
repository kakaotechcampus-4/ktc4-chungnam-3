package com.ktc.chungnam3.remembrall.bruno;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class BrunoHarnessTest {
    private Map<String, String> environment(String url, String token) {
        return Map.of("SPRING_DATASOURCE_URL", url, "BRUNO_HARNESS_TOKEN", token);
    }
    @Test void rejectsProductionUrlBeforeStartingSpring() {
        assertThatThrownBy(() -> BrunoHarness.validateEnvironment(
                environment("jdbc:postgresql://127.0.0.1:5432/remembrall", "a".repeat(64)), new String[0]))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsWeakTokenBeforeStartingSpring() {
        assertThatThrownBy(() -> BrunoHarness.validateEnvironment(
                environment("jdbc:postgresql://127.0.0.1:12345/bruno_regression", "short"), new String[0]))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void preventsCommandLineDatasourceOverrides() {
        assertThatThrownBy(() -> BrunoHarness.validateEnvironment(
                environment("jdbc:postgresql://127.0.0.1:12345/bruno_regression", "a".repeat(64)),
                new String[]{"--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/remembrall"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsRunnerConfiguration() {
        assertThatCode(() -> BrunoHarness.validateEnvironment(
                environment("jdbc:postgresql://127.0.0.1:12345/bruno_regression", "a".repeat(64)),
                new String[]{"--server.port=18082"})).doesNotThrowAnyException();
    }
}
