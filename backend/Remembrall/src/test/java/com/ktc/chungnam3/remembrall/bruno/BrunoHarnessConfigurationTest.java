package com.ktc.chungnam3.remembrall.bruno;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrunoHarnessConfigurationTest {
    private final BrunoHarnessConfiguration configuration = new BrunoHarnessConfiguration();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("BRUNO_HARNESS_TOKEN", "a".repeat(64))
                .withProperty("server.address", "127.0.0.1");
    }

    @Test void refusesProductionDatabase() {
        when(jdbc.queryForObject("SELECT current_database()", String.class)).thenReturn("remembrall");
        assertThatThrownBy(() -> configuration.harnessState(jdbc, environment(), executor))
                .isInstanceOf(IllegalStateException.class);
        verify(jdbc, never()).execute(anyString());
    }

    @Test void refusesPublicBindAddress() {
        assertThatThrownBy(() -> configuration.harnessState(jdbc,
                environment().withProperty("server.address", "0.0.0.0"), executor)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void refusesWeakToken() {
        assertThatThrownBy(() -> configuration.harnessState(jdbc,
                environment().withProperty("BRUNO_HARNESS_TOKEN", "short"), executor)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void acceptsOnlyDisposableConfiguration() {
        when(jdbc.queryForObject("SELECT current_database()", String.class)).thenReturn("bruno_regression");
        assertThat(configuration.harnessState(jdbc, environment(), executor)).isNotNull();
    }
}
