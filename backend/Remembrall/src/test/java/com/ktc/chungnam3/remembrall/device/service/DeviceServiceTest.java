package com.ktc.chungnam3.remembrall.device.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

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
    private PlatformTransactionManager transactionManager;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        memberRepository.deleteAll();
        deviceService = new DeviceService(
                memberRepository,
                deviceRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
        );
    }

    @Test
    void movesFcmTokenToCurrentAuthenticatedDevice() {
        Device previousDevice = saveDevice("previous", "a".repeat(64), "shared-fcm-token");
        Device currentDevice = saveDevice("current", "b".repeat(64), null);

        deviceService.updateFcmToken(
                currentDevice.getMemberId(),
                currentDevice.getId(),
                currentDevice.getSessionTokenHash(),
                "shared-fcm-token"
        );

        assertThat(deviceRepository.findById(previousDevice.getId()).orElseThrow().getFcmToken()).isNull();
        assertThat(deviceRepository.findById(currentDevice.getId()).orElseThrow().getFcmToken())
                .isEqualTo("shared-fcm-token");
    }

    @Test
    void rollsBackOtherDeviceDetachWhenCurrentSessionDoesNotMatch() {
        Device previousDevice = saveDevice("previous", "c".repeat(64), "shared-fcm-token");
        Device currentDevice = saveDevice("current", "d".repeat(64), null);

        assertThatThrownBy(() -> deviceService.updateFcmToken(
                currentDevice.getMemberId(),
                currentDevice.getId(),
                "e".repeat(64),
                "shared-fcm-token"
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SESSION));

        assertThat(deviceRepository.findById(previousDevice.getId()).orElseThrow().getFcmToken())
                .isEqualTo("shared-fcm-token");
        assertThat(deviceRepository.findById(currentDevice.getId()).orElseThrow().getFcmToken()).isNull();
    }

    @Test
    void concurrentRegistrationOfSameFcmTokenCompletesWithOneOwner() throws Exception {
        Device firstDevice = saveDevice("first", "f".repeat(64), null);
        Device secondDevice = saveDevice("second", "0".repeat(64), null);
        String fcmToken = "concurrent-fcm-token";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(
                    () -> registerAfterBarrier(firstDevice, fcmToken, ready, start));
            Future<?> second = executor.submit(
                    () -> registerAfterBarrier(secondDevice, fcmToken, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);

            List<Device> devices = deviceRepository.findAll();
            assertThat(devices.stream()
                    .filter(device -> fcmToken.equals(device.getFcmToken()))
                    .count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Device saveDevice(String providerUserId, String sessionTokenHash, String fcmToken) {
        Member member = memberRepository.saveAndFlush(
                Member.create(AuthProvider.KAKAO, providerUserId + UUID.randomUUID(), NOW));
        Device device = Device.create(
                member.getId(),
                sessionTokenHash,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60)
        );
        if (fcmToken != null) {
            device.updateFcmToken(fcmToken, NOW.minusSeconds(60));
        }
        return deviceRepository.saveAndFlush(device);
    }

    private void registerAfterBarrier(
            Device device,
            String fcmToken,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        deviceService.updateFcmToken(
                device.getMemberId(),
                device.getId(),
                device.getSessionTokenHash(),
                fcmToken
        );
    }
}
