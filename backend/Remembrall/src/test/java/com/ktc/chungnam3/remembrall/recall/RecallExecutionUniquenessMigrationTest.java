package com.ktc.chungnam3.remembrall.recall;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RecallExecutionUniquenessMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Test
    void upgradesAppliedV8ToTriggerScopedUniquenessWithoutChangingExistingExecution() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("8")
                .load()
                .migrate();

        UUID placeId = UUID.randomUUID();
        update("""
                INSERT INTO place (
                    id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                    verification_provider, verification_place_id, verified_at
                )
                VALUES (?, 'Place', 36.35, 127.38, 'LOCATIONIQ', ?, 'KAKAO', ?, '2026-10-02T00:00:00Z')
                """, placeId, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        UUID firstTriggerId = insertTrigger(placeId);
        UUID secondTriggerId = insertTrigger(placeId);
        UUID eventId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        assertThat(insertExecution(originalId, firstTriggerId, eventId)).isOne();
        assertThatThrownBy(() -> insertExecution(UUID.randomUUID(), secondTriggerId, eventId))
                .isInstanceOf(SQLException.class).hasMessageContaining("uk_recall_execution_trigger_event");

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("9")
                .load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");

        assertThat(insertExecution(UUID.randomUUID(), secondTriggerId, eventId)).isOne();
        assertThatThrownBy(() -> insertExecution(UUID.randomUUID(), firstTriggerId, eventId))
                .isInstanceOf(SQLException.class).hasMessageContaining("uk_recall_execution_trigger_event");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT trigger_id, trigger_event_id, status, agent_version FROM recall_execution WHERE id = ?
                     """)) {
            statement.setObject(1, originalId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("trigger_id", UUID.class)).isEqualTo(firstTriggerId);
                assertThat(rows.getObject("trigger_event_id", UUID.class)).isEqualTo(eventId);
                assertThat(rows.getString("status")).isEqualTo("PENDING");
                assertThat(rows.getString("agent_version")).isEqualTo("stub-v1");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private UUID insertTrigger(UUID placeId) throws SQLException {
        UUID memberId = UUID.randomUUID();
        UUID triggerId = UUID.randomUUID();
        update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'KAKAO', ?)",
                memberId, UUID.randomUUID().toString());
        update("INSERT INTO trigger (id, member_id, place_id) VALUES (?, ?, ?)", triggerId, memberId, placeId);
        return triggerId;
    }

    private int insertExecution(UUID id, UUID triggerId, UUID eventId) throws SQLException {
        return update("""
                INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, agent_version, event_occurred_at)
                VALUES (?, ?, ?, 'PENDING', 'stub-v1', '2026-10-02T00:00:00Z')
                """, id, triggerId, eventId);
    }

    private int update(String sql, Object... arguments) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
