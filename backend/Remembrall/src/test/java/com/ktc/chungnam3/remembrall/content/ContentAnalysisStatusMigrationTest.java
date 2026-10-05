package com.ktc.chungnam3.remembrall.content;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ContentAnalysisStatusMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Test
    void migratesExistingTerminalStatusesAndReplacesCheckConstraint() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("4")
                .load()
                .migrate();

        insertContent("completed-video", "COMPLETED");
        insertContent("partial-video", "PARTIAL_SUCCESS");
        insertContent("failed-video", "FAILED");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        assertThat(findStatusesByVideoId())
                .containsEntry("completed-video", "SUCCESS")
                .containsEntry("partial-video", "PARTIAL")
                .containsEntry("failed-video", "FAILED");
        assertThatThrownBy(() -> insertContent("legacy-video", "COMPLETED"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_content_analysis_status");
    }

    private void insertContent(String videoId, String status) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO content (id, video_id, analysis_status)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, videoId);
            statement.setString(3, status);
            statement.executeUpdate();
        }
    }

    private Map<String, String> findStatusesByVideoId() throws SQLException {
        Map<String, String> statuses = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT video_id, analysis_status
                     FROM content
                     ORDER BY video_id
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                statuses.put(resultSet.getString("video_id"), resultSet.getString("analysis_status"));
            }
        }
        return statuses;
    }
}
