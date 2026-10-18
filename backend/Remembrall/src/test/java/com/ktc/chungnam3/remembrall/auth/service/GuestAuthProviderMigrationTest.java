package com.ktc.chungnam3.remembrall.auth.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class GuestAuthProviderMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void appliedV14UpgradesToAllowGuestsWhilePreservingExistingKakaoMemberAndRejectingUnknownProvider() {
        flyway("14").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID kakaoId = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'KAKAO', '12345')", kakaoId);
        var existing = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", kakaoId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'GUEST', ?)",
                UUID.randomUUID(), "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_member_auth_provider");

        flyway("15").migrate();

        assertThat(jdbc.queryForMap("SELECT * FROM member WHERE id = ?", kakaoId)).isEqualTo(existing);
        UUID guestId = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'GUEST', ?)",
                guestId, "a".repeat(64));
        assertThat(jdbc.queryForObject("SELECT auth_provider FROM member WHERE id = ?", String.class, guestId))
                .isEqualTo("GUEST");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'UNKNOWN', 'user')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_member_auth_provider");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(target).load();
    }
}
