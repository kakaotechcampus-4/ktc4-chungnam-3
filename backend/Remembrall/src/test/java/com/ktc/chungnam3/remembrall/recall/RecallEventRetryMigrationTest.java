package com.ktc.chungnam3.remembrall.recall;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RecallEventRetryMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void v20PreservesExistingExecutionsAndTracesAndInitializesAttemptCounts() {
        flyway("19").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID memberId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'GUEST', ?)", memberId, memberId.toString());
        jdbc.update("""
                INSERT INTO place (id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, 'Place', 36.35, 127.38, 'LOCATIONIQ', ?, 'KAKAO', ?, CURRENT_TIMESTAMP)
                """, placeId, placeId.toString(), placeId.toString());
        jdbc.update("INSERT INTO trigger (id, member_id, place_id) VALUES (?, ?, ?)", triggerId, memberId, placeId);
        jdbc.update("""
                INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, agent_version, event_occurred_at, failure_code)
                VALUES (?, ?, ?, 'FAILED', 'old-agent', CURRENT_TIMESTAMP, 'OLD_FAILURE')
                """, executionId, triggerId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO recall_execution_trace (id, execution_id, seq, step, started_at, duration_ms, success, detail)
                VALUES (?, ?, 1, 'MODEL_CALL', CURRENT_TIMESTAMP, 1, false, '{}'::jsonb)
                """, traceId, executionId);
        var originalExecution = jdbc.queryForMap("SELECT * FROM recall_execution WHERE id = ?", executionId);
        var originalTrace = jdbc.queryForMap("SELECT * FROM recall_execution_trace WHERE id = ?", traceId);

        var flyway = flyway("20");
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("20");
        var execution = jdbc.queryForMap("SELECT * FROM recall_execution WHERE id = ?", executionId);
        assertThat(execution.remove("retry_count")).isEqualTo(0);
        assertThat(execution).isEqualTo(originalExecution);
        var trace = jdbc.queryForMap("SELECT * FROM recall_execution_trace WHERE id = ?", traceId);
        assertThat(trace.remove("attempt")).isEqualTo(1);
        assertThat(trace).isEqualTo(originalTrace);
        assertThatThrownBy(() -> jdbc.update("UPDATE recall_execution SET retry_count=-1 WHERE id=?", executionId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE recall_execution_trace SET attempt=0 WHERE id=?", traceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(target).load();
    }
}
