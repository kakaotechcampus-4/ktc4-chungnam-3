package com.ktc.chungnam3.remembrall.auth.service;

import com.ktc.chungnam3.remembrall.auth.dto.SessionResponse;
import com.ktc.chungnam3.remembrall.auth.kakao.KakaoUserClient;
import com.ktc.chungnam3.remembrall.auth.token.SessionProperties;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenGenerator;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceConcurrencyTest {

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

    @Test
    void concurrentLoginForSameKakaoAccountLeavesOneMemberDeviceAndSession() throws Exception {
        KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
        when(kakaoUserClient.getProviderUserId(anyString())).thenReturn("same-kakao-user");
        SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
        SessionTokenHasher tokenHasher = new SessionTokenHasher();
        AuthService authService = new AuthService(
                kakaoUserClient,
                memberRepository,
                deviceRepository,
                tokenGenerator,
                tokenHasher,
                new SessionProperties(Duration.ofDays(30)),
                Clock.systemUTC(),
                transactionManager
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SessionResponse> first = executor.submit(
                    () -> loginAfterBarrier(authService, "kakao-token-1", ready, start));
            Future<SessionResponse> second = executor.submit(
                    () -> loginAfterBarrier(authService, "kakao-token-2", ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            SessionResponse firstResponse = first.get(20, TimeUnit.SECONDS);
            SessionResponse secondResponse = second.get(20, TimeUnit.SECONDS);

            assertThat(memberRepository.count()).isOne();
            assertThat(deviceRepository.count()).isOne();
            assertThat(firstResponse.sessionToken()).isNotEqualTo(secondResponse.sessionToken());

            Device finalDevice = deviceRepository.findAll().getFirst();
            List<String> issuedHashes = List.of(
                    tokenHasher.hash(firstResponse.sessionToken()),
                    tokenHasher.hash(secondResponse.sessionToken())
            );
            assertThat(finalDevice.getSessionTokenHash()).isIn(issuedHashes);
            assertThat(issuedHashes.stream()
                    .filter(hash -> deviceRepository.findBySessionTokenHash(hash).isPresent())
                    .count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private SessionResponse loginAfterBarrier(
            AuthService authService,
            String kakaoToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return authService.loginWithKakao(kakaoToken);
    }
}
