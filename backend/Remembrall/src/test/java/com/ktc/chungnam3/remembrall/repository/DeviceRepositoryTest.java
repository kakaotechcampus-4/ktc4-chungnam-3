package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class DeviceRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-23T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16")
    );

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void clearsSessionAndFcmWithoutDeletingDeviceWhenEveryConditionMatches() {
        UUID memberId = insertMember();
        String sessionTokenHash = "a".repeat(64);
        Device device = saveDevice(memberId, sessionTokenHash);
        Instant loggedOutAt = CREATED_AT.plusSeconds(60);

        int updatedRows = deviceRepository.clearCurrentSession(
                device.getId(),
                memberId,
                sessionTokenHash,
                loggedOutAt
        );
        entityManager.clear();

        Device loggedOutDevice = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(updatedRows).isOne();
        assertThat(loggedOutDevice.getSessionTokenHash()).isNull();
        assertThat(loggedOutDevice.getSessionExpiresAt()).isNull();
        assertThat(loggedOutDevice.getFcmToken()).isNull();
        assertThat(loggedOutDevice.getUpdatedAt()).isEqualTo(loggedOutAt);
    }

    @Test
    void doesNotClearNewSessionWhenDeviceMemberOrSessionHashDoesNotMatch() {
        UUID memberId = insertMember();
        String sessionTokenHash = "b".repeat(64);
        Device device = saveDevice(memberId, sessionTokenHash);

        int wrongDeviceRows = deviceRepository.clearCurrentSession(
                UUID.randomUUID(),
                memberId,
                sessionTokenHash,
                CREATED_AT.plusSeconds(60)
        );
        int wrongMemberRows = deviceRepository.clearCurrentSession(
                device.getId(),
                UUID.randomUUID(),
                sessionTokenHash,
                CREATED_AT.plusSeconds(60)
        );
        int oldSessionRows = deviceRepository.clearCurrentSession(
                device.getId(),
                memberId,
                "c".repeat(64),
                CREATED_AT.plusSeconds(60)
        );
        entityManager.clear();

        Device activeDevice = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(wrongDeviceRows).isZero();
        assertThat(wrongMemberRows).isZero();
        assertThat(oldSessionRows).isZero();
        assertThat(activeDevice.getSessionTokenHash()).isEqualTo(sessionTokenHash);
        assertThat(activeDevice.getFcmToken()).isEqualTo("fcm-token");
        assertThat(activeDevice.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    private UUID insertMember() {
        UUID memberId = UUID.randomUUID();
        memberRepository.insertIfAbsent(
                memberId,
                AuthProvider.KAKAO.name(),
                UUID.randomUUID().toString(),
                CREATED_AT
        );
        return memberId;
    }

    private Device saveDevice(UUID memberId, String sessionTokenHash) {
        Device device = Device.create(
                memberId,
                sessionTokenHash,
                CREATED_AT.plusSeconds(3600),
                CREATED_AT
        );
        device.updateFcmToken("fcm-token", CREATED_AT);
        return deviceRepository.saveAndFlush(device);
    }
}
