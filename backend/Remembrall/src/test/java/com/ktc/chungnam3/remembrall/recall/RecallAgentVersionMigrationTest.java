package com.ktc.chungnam3.remembrall.recall;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RecallAgentVersionMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void widensVersionToTwoHundredCharactersWithoutChangingExistingExecution() {
        flyway("12").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID memberId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'KAKAO', ?)",
                memberId, UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO place (id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, 'Place', 36.35, 127.38, 'LOCATIONIQ', ?, 'KAKAO', ?, '2026-10-02T00:00:00Z')
                """, placeId, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        jdbc.update("INSERT INTO trigger (id, member_id, place_id) VALUES (?, ?, ?)", triggerId, memberId, placeId);
        insert(jdbc, executionId, triggerId, "stub-v1");
        var existing = jdbc.queryForMap("SELECT * FROM recall_execution WHERE id = ?", executionId);

        flyway("13").migrate();

        assertThat(jdbc.queryForMap("SELECT * FROM recall_execution WHERE id = ?", executionId)).isEqualTo(existing);
        assertThat(jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'recall_execution' AND column_name = 'agent_version'
                """, Integer.class)).isEqualTo(200);
        UUID longExecutionId = UUID.randomUUID();
        String prefix = "llm|openai|";
        String suffix = "|prompt-v1-abcdef";
        String longVersion = prefix + "m".repeat(200 - prefix.length() - suffix.length()) + suffix;
        insert(jdbc, longExecutionId, triggerId, longVersion);
        assertThat(longVersion.length()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT agent_version FROM recall_execution WHERE id = ?",
                String.class, longExecutionId)).isEqualTo(longVersion);
    }

    private void insert(JdbcTemplate jdbc, UUID id, UUID triggerId, String version) {
        jdbc.update("""
                INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, agent_version, event_occurred_at)
                VALUES (?, ?, ?, 'PENDING', ?, '2026-10-02T00:00:00Z')
                """, id, triggerId, UUID.randomUUID(), version);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(target).load();
    }
}
