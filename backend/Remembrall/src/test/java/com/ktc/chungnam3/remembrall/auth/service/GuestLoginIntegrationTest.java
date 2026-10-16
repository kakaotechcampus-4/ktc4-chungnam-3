package com.ktc.chungnam3.remembrall.auth.service;

import com.ktc.chungnam3.remembrall.auth.dto.SessionResponse;
import com.ktc.chungnam3.remembrall.auth.controller.AuthController;
import com.ktc.chungnam3.remembrall.auth.kakao.KakaoUserClient;
import com.ktc.chungnam3.remembrall.auth.security.ApiAuthenticationEntryPoint;
import com.ktc.chungnam3.remembrall.auth.security.SecurityConfig;
import com.ktc.chungnam3.remembrall.auth.security.SessionAuthenticationFilter;
import com.ktc.chungnam3.remembrall.auth.security.SessionConfig;
import com.ktc.chungnam3.remembrall.auth.token.SessionProperties;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenGenerator;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.common.exception.GlobalExceptionHandler;
import com.ktc.chungnam3.remembrall.consent.config.ConsentConfiguration;
import com.ktc.chungnam3.remembrall.consent.controller.MemberConsentController;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.MemberConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = GuestLoginIntegrationTest.AuthTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key",
        "youtube.api.key=test-api-key", "kakao.app-id=1", "dataportal.api.key=test-api-key",
        "locationiq.api.key=test-api-key"
})
@Import(GuestLoginIntegrationTest.ClockConfiguration.class)
class GuestLoginIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private KakaoUserClient kakao;
    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member");
        reset(kakao);
    }

    @Test
    void anonymousGuestLoginCreatesMemberAndSessionWithoutPersistingRawCredential() throws Exception {
        String installationId = UUID.randomUUID().toString();

        var response = postGuest(installationId);
        var session = session(response);

        assertThat(mapper.readTree(response.body()).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("sessionToken", "sessionExpiresAt");
        assertThat(members.count()).isOne();
        assertThat(devices.count()).isOne();
        var member = members.findAll().getFirst();
        var device = devices.findAll().getFirst();
        assertThat(member.getAuthProvider()).isEqualTo(AuthProvider.GUEST);
        assertThat(member.getProviderUserId()).isEqualTo(credentialHash(installationId)).hasSize(64);
        assertThat(device.getMemberId()).isEqualTo(member.getId());
        assertThat(device.getSessionTokenHash()).isEqualTo(hasher.hash(session.sessionToken()));
        assertThat(device.getSessionExpiresAt()).isEqualTo(session.sessionExpiresAt());
        assertThat(session.sessionExpiresAt()).isEqualTo(NOW.plus(sessionProperties.ttl()));
        assertAbsentFromDatabase(installationId);
        assertAbsentFromDatabase(session.sessionToken());
        assertThat(protectedRequest(session.sessionToken()).statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(protectedRequest(session.sessionToken()).body()).size()).isEqualTo(2);
        verifyNoInteractions(kakao);
    }

    @Test
    void repeatedInstallationReusesMemberAndDeviceAndInvalidatesPreviousSession() throws Exception {
        String installationId = UUID.randomUUID().toString();
        var first = session(postGuest(installationId));
        var memberId = members.findAll().getFirst().getId();
        var device = devices.findAll().getFirst();
        var deviceId = device.getId();
        device.updateFcmToken("old-fcm-token", NOW);
        devices.saveAndFlush(device);
        assertThat(protectedRequest(first.sessionToken()).statusCode()).isEqualTo(200);

        var second = session(postGuest(installationId));

        assertThat(members.count()).isOne();
        assertThat(members.findAll().getFirst().getId()).isEqualTo(memberId);
        assertThat(devices.count()).isOne();
        var current = devices.findAll().getFirst();
        assertThat(current.getId()).isEqualTo(deviceId);
        assertThat(current.getMemberId()).isEqualTo(memberId);
        assertThat(current.getFcmToken()).isNull();
        assertThat(second.sessionToken()).isNotEqualTo(first.sessionToken());
        assertError(protectedRequest(first.sessionToken()), 401, ErrorCode.INVALID_SESSION);
        assertThat(protectedRequest(second.sessionToken()).statusCode()).isEqualTo(200);
        verifyNoInteractions(kakao);
    }

    @Test
    void differentUuidLetterCasesIdentifySameGuest() throws Exception {
        String installationId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        var first = session(postGuest(installationId));

        var second = session(postGuest(installationId.toUpperCase(Locale.ROOT)));

        assertThat(members.count()).isOne();
        assertThat(members.findAll().getFirst().getProviderUserId()).isEqualTo(credentialHash(installationId));
        assertError(protectedRequest(first.sessionToken()), 401, ErrorCode.INVALID_SESSION);
        assertThat(protectedRequest(second.sessionToken()).statusCode()).isEqualTo(200);
    }

    @Test
    void differentInstallationsHaveIndependentMembersAndSessions() throws Exception {
        var first = session(postGuest(UUID.randomUUID().toString()));
        var second = session(postGuest(UUID.randomUUID().toString()));

        assertThat(members.count()).isEqualTo(2);
        assertThat(devices.count()).isEqualTo(2);
        assertThat(protectedRequest(first.sessionToken()).statusCode()).isEqualTo(200);
        assertThat(protectedRequest(second.sessionToken()).statusCode()).isEqualTo(200);
    }

    @Test
    void kakaoStillUsesExternalProviderIdAndRotatesOnlyItsOwnSession() throws Exception {
        var guest = session(postGuest(UUID.randomUUID().toString()));
        when(kakao.getProviderUserId("kakao-token")).thenReturn("12345");
        var first = session(post("/api/auth/kakao", "{\"kakaoAccessToken\":\"kakao-token\"}"));
        UUID kakaoMemberId = devices.findBySessionTokenHash(hasher.hash(first.sessionToken())).orElseThrow().getMemberId();

        var second = session(post("/api/auth/kakao", "{\"kakaoAccessToken\":\"kakao-token\"}"));

        assertThat(members.count()).isEqualTo(2);
        assertThat(devices.count()).isEqualTo(2);
        var kakaoMember = members.findById(kakaoMemberId).orElseThrow();
        assertThat(kakaoMember.getAuthProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(kakaoMember.getProviderUserId()).isEqualTo("12345");
        assertThat(devices.findBySessionTokenHash(hasher.hash(second.sessionToken())).orElseThrow().getMemberId())
                .isEqualTo(kakaoMemberId);
        assertError(protectedRequest(first.sessionToken()), 401, ErrorCode.INVALID_SESSION);
        assertThat(protectedRequest(second.sessionToken()).statusCode()).isEqualTo(200);
        assertThat(protectedRequest(guest.sessionToken()).statusCode()).isEqualTo(200);
        verify(kakao, times(2)).getProviderUserId("kakao-token");
    }

    @Test
    void simultaneousFirstGuestLoginsCreateOneMemberAndOneCurrentSession() throws Exception {
        String installationId = UUID.randomUUID().toString();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> requests = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.execute("LOCK TABLE member IN SHARE MODE");
                    int holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                    for (int index = 0; index < 2; index++) {
                        requests.add(executor.submit(() -> {
                            ready.countDown();
                            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                            return postGuest(installationId);
                        }));
                    }
                    try {
                        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                        start.countDown();
                        awaitBlockedGuestInserts(holderPid);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
                var first = session(requests.get(0).get(20, TimeUnit.SECONDS));
                var second = session(requests.get(1).get(20, TimeUnit.SECONDS));

                assertThat(members.count()).isOne();
                assertThat(devices.count()).isOne();
                assertThat(members.findAll().getFirst().getProviderUserId()).isEqualTo(credentialHash(installationId));
                assertThat(first.sessionToken()).isNotEqualTo(second.sessionToken());
                assertThat(List.of(protectedRequest(first.sessionToken()).statusCode(),
                        protectedRequest(second.sessionToken()).statusCode())).containsExactlyInAnyOrder(200, 401);
                assertThat(devices.findAll().getFirst().getSessionTokenHash())
                        .isIn(hasher.hash(first.sessionToken()), hasher.hash(second.sessionToken()));
                verifyNoInteractions(kakao);
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"installationId\":null}", "{\"installationId\":\"\"}", "{\"installationId\":\"   \"}",
            "{\"installationId\":\"not-a-uuid\"}", "{\"installationId\":\"1-1-1-1-1\"}",
            "{\"installationId\":\"f47ac10b58cc4372a5670e02b2c3d479\"}",
            "{\"installationId\":\"AAAAAAAAAAAAAAAAAAAAAA==\"}",
            "{\"installationId\":\"f47ac10b-58cc-4372-a567-0e02b2c3d4790\"}",
            "{\"installationId\":123}", "{\"installationId\":{}}", "{\"installationId\":[]}", "{"
    })
    void invalidGuestCredentialReturns400WithoutRequiringSession(String body) throws Exception {
        assertError(post("/api/auth/guest", body), 400, ErrorCode.INVALID_REQUEST);
        assertThat(members.count()).isZero();
        assertThat(devices.count()).isZero();
        verifyNoInteractions(kakao);
    }

    private void awaitBlockedGuestInserts(int holderPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            int blocked = jdbc.queryForObject("""
                    WITH RECURSIVE blocked(pid) AS (
                        SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                        UNION
                        SELECT activity.pid FROM pg_stat_activity activity
                        JOIN blocked ON blocked.pid = ANY(pg_blocking_pids(activity.pid))
                    )
                    SELECT COUNT(*) FROM blocked
                    """, Integer.class, holderPid);
            if (blocked == 2) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Both guest requests must reach member insertion concurrently");
    }

    private String credentialHash(String installationId) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(installationId.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertAbsentFromDatabase(String secret) {
        var tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class);
        for (String table : tables) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"" + table + "\" entry WHERE strpos(to_jsonb(entry)::text, ?) > 0",
                    Integer.class, secret)).as("raw credential absent from %s", table).isZero();
        }
    }

    private HttpResponse<String> postGuest(String installationId) throws Exception {
        return post("/api/auth/guest", mapper.writeValueAsString(Map.of("installationId", installationId)));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30)).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> protectedRequest(String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/consents"))
                .timeout(Duration.ofSeconds(10)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private SessionResponse session(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var session = mapper.readValue(response.body(), SessionResponse.class);
        assertThat(session.sessionToken()).isNotBlank();
        assertThat(session.sessionExpiresAt()).isAfter(NOW);
        return session;
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        Clock guestTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Member.class, Device.class, MemberConsent.class})
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class, includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {MemberRepository.class, DeviceRepository.class, MemberConsentRepository.class}))
    @Import({AuthController.class, AuthService.class, SecurityConfig.class, SessionConfig.class,
            SessionAuthenticationFilter.class, ApiAuthenticationEntryPoint.class, SessionTokenGenerator.class,
            SessionTokenHasher.class, GlobalExceptionHandler.class, ConsentConfiguration.class,
            MemberConsentController.class, MemberConsentService.class})
    static class AuthTestApplication {}
}
