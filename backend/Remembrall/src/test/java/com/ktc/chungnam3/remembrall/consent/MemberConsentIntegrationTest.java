package com.ktc.chungnam3.remembrall.consent;

import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentResponse;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberConsentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key",
        "youtube.api.key=test-api-key", "kakao.app-id=1",
        "dataportal.api.key=test-api-key", "locationiq.api.key=test-api-key",
        "consent.public-candidate-contribution-terms-version=v1", "consent.location-based-service-terms-version=v1"
})
@Import(MemberConsentIntegrationTest.ClockConfiguration.class)
class MemberConsentIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private MemberConsentRepository consents;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TestClock clock;
    @MockitoSpyBean private ConsentProperties properties;
    @LocalServerPort private int port;

    private TransactionTemplate transactions;
    private Fixture fixture;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeEach
    void setUp() {
        reset(properties);
        clock.set(NOW);
        jdbc.update("DELETE FROM member");
        transactions = new TransactionTemplate(transactionManager);
        fixture = fixture();
    }

    @Test
    void neverAgreedReturnsBothTypesWithNullHistory() throws Exception {
        assertThat(list(fixture)).containsExactly(
                new ConsentResponse(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION, false, null, null, null),
                new ConsentResponse(ConsentType.LOCATION_BASED_SERVICE, false, null, null, null));
        assertThat(consents.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ConsentType.class)
    void agreementIsActiveAndSameVersionIsIdempotent(ConsentType type) throws Exception {
        ConsentResponse first = put(type, true, "v1");
        assertThat(first).isEqualTo(new ConsentResponse(type, true, "v1", NOW, null));
        assertThat(list(fixture)).contains(first);
        MemberConsent active = consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)
                .orElseThrow();
        assertThat(active.getCreatedAt()).isEqualTo(NOW);

        clock.set(NOW.plusSeconds(30));
        assertThat(put(type, true, "v1")).isEqualTo(first);
        assertThat(consents.count()).isOne();
        assertThat(consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)
                .orElseThrow().getId()).isEqualTo(active.getId());
    }

    @ParameterizedTest
    @EnumSource(ConsentType.class)
    void versionRotationWithdrawsPreviousRowAndCreatesNewHistory(ConsentType type) throws Exception {
        put(type, true, "v1");
        MemberConsent previous = consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)
                .orElseThrow();
        Instant changeTime = NOW.plusSeconds(30);
        clock.set(changeTime);
        doReturn("v2").when(properties).currentVersion(type);

        ConsentResponse current = put(type, true, "v2");

        assertThat(current).isEqualTo(new ConsentResponse(type, true, "v2", changeTime, null));
        assertThat(consents.count()).isEqualTo(2);
        assertThat(consents.findById(previous.getId()).orElseThrow().getWithdrawnAt()).isEqualTo(changeTime);
        assertThat(consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)
                .orElseThrow().getId()).isNotEqualTo(previous.getId());
        assertThat(list(fixture)).contains(current);
        assertError(request("PUT", "/" + type, new ConsentRequest(true, "v1"), fixture), 400, ErrorCode.INVALID_REQUEST);
        assertThat(consents.count()).isEqualTo(2);
        assertThat(list(fixture)).contains(current);

        clock.set(NOW.plusSeconds(60));
        ConsentResponse withdrawn = put(type, false, null);
        assertThat(withdrawn).isEqualTo(new ConsentResponse(type, false, "v2", changeTime, clock.instant()));
        assertThat(list(fixture)).contains(withdrawn);
    }

    @ParameterizedTest
    @EnumSource(ConsentType.class)
    void withdrawalAndReagreementRetainTwoHistoryRows(ConsentType type) throws Exception {
        put(type, true, "v1");
        Instant withdrawnAt = NOW.plusSeconds(30);
        clock.set(withdrawnAt);
        ConsentResponse withdrawn = put(type, false, null);
        assertThat(withdrawn).isEqualTo(new ConsentResponse(type, false, "v1", NOW, withdrawnAt));
        assertThat(list(fixture)).contains(withdrawn);
        assertThat(consents.existsByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)).isFalse();

        clock.set(NOW.plusSeconds(60));
        assertThat(put(type, false, null)).isEqualTo(withdrawn);
        assertThat(consents.count()).isOne();
        ConsentResponse again = put(type, true, "v1");
        assertThat(again).isEqualTo(new ConsentResponse(type, true, "v1", clock.instant(), null));
        assertThat(consents.count()).isEqualTo(2);
        assertThat(consents.findAll()).filteredOn(consent -> consent.getWithdrawnAt() != null).hasSize(1);
        assertThat(list(fixture)).contains(again);
    }

    @ParameterizedTest
    @EnumSource(ConsentType.class)
    void withdrawalWithoutActiveConsentDoesNotInsertHistory(ConsentType type) throws Exception {
        ConsentResponse response = put(type, false, null);
        assertThat(response).isEqualTo(new ConsentResponse(type, false, null, null, null));
        assertThat(consents.count()).isZero();
        assertThat(list(fixture)).contains(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v0", "v2", "", "null"})
    void rejectsInvalidOrMissingTermsVersion(String version) throws Exception {
        String terms = "null".equals(version) ? null : version;
        for (ConsentType type : ConsentType.values()) {
            assertError(request("PUT", "/" + type, new ConsentRequest(true, terms), fixture),
                    400, ErrorCode.INVALID_REQUEST);
        }
        assertThat(consents.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "location_based_service", "PUBLIC", "0"})
    void rejectsUndefinedConsentType(String type) throws Exception {
        assertError(request("PUT", "/" + type, new ConsentRequest(true, "v1"), fixture),
                400, ErrorCode.INVALID_REQUEST);
        assertThat(consents.count()).isZero();
    }

    @Test
    void agreedIsRequiredAndWithdrawalDoesNotValidateTermsVersion() throws Exception {
        assertError(request("PUT", "/LOCATION_BASED_SERVICE", new ConsentRequest(null, "v1"), fixture),
                400, ErrorCode.INVALID_REQUEST);
        put(ConsentType.LOCATION_BASED_SERVICE, true, "v1");
        assertThat(put(ConsentType.LOCATION_BASED_SERVICE, false, "obsolete").agreed()).isFalse();
        assertThat(consents.count()).isOne();
    }

    @Test
    void typesAndMembersHaveIndependentConsentState() throws Exception {
        ConsentResponse publicConsent = put(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION, true, "v1");
        ConsentResponse locationConsent = put(ConsentType.LOCATION_BASED_SERVICE, true, "v1");
        assertThat(list(fixture)).containsExactly(publicConsent, locationConsent);
        Fixture other = fixture();
        assertThat(list(other)).allMatch(response -> !response.agreed() && response.termsVersion() == null);
        var response = request("PUT", "/LOCATION_BASED_SERVICE", new ConsentRequest(false, null), other);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(list(fixture)).containsExactly(publicConsent, locationConsent);
        assertThat(consents.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(ConsentType.class)
    void simultaneousAgreementsWaitOnMemberAndCreateOneActiveRow(ConsentType type) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var responses = transactions.execute(status -> {
                members.findByIdForUpdate(fixture.memberId()).orElseThrow();
                Integer holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                var first = executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return request("PUT", "/" + type, new ConsentRequest(true, "v1"), fixture);
                });
                var second = executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return request("PUT", "/" + type, new ConsentRequest(true, "v1"), fixture);
                });
                try {
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    awaitBlockedRequests(holderPid);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    start.countDown();
                }
                return List.of(first, second);
            });
            for (var pending : responses) {
                var response = pending.get(30, TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(mapper.readValue(response.body(), ConsentResponse.class))
                        .isEqualTo(new ConsentResponse(type, true, "v1", NOW, null));
            }
        }
        assertThat(consents.count()).isOne();
        assertThat(consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(fixture.memberId(), type)).isPresent();
    }

    @Test
    void getAndPutRequireAValidSession() throws Exception {
        for (Fixture session : Arrays.asList(null, fixture)) {
            if (session != null) {
                jdbc.update("UPDATE device SET session_expires_at = ? WHERE member_id = ?",
                        java.sql.Timestamp.from(NOW), fixture.memberId());
            }
            assertError(request("GET", "", null, session), 401, ErrorCode.INVALID_SESSION);
            assertError(request("PUT", "/LOCATION_BASED_SERVICE", new ConsentRequest(true, "v1"), session),
                    401, ErrorCode.INVALID_SESSION);
        }
        assertThat(consents.count()).isZero();
    }

    @Test
    void memberDeletionCascadesAllConsentHistory() throws Exception {
        put(ConsentType.LOCATION_BASED_SERVICE, true, "v1");
        put(ConsentType.LOCATION_BASED_SERVICE, false, null);
        clock.set(NOW.plusSeconds(30));
        put(ConsentType.LOCATION_BASED_SERVICE, true, "v1");
        put(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION, true, "v1");
        assertThat(consents.count()).isEqualTo(3);

        members.deleteById(fixture.memberId());

        assertThat(consents.count()).isZero();
    }

    @Test
    void schemaKeepsConsentSeparateAndPartialUniquenessIsEnforced() throws Exception {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'member_consent'
                ORDER BY ordinal_position
                """, String.class)).containsExactly("id", "member_id", "consent_type", "terms_version",
                "agreed_at", "withdrawn_at", "created_at");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_member_consent_active'",
                String.class)).contains("UNIQUE INDEX", "(member_id, consent_type)", "WHERE (withdrawn_at IS NULL)");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name IN ('content', 'personal_save')
                  AND column_name LIKE '%consent%'
                """, String.class)).isEmpty();
        put(ConsentType.LOCATION_BASED_SERVICE, true, "v1");
        assertThatThrownBy(() -> insertConsent("LOCATION_BASED_SERVICE"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_member_consent_active");
        assertThatThrownBy(() -> insertConsent("UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_member_consent_type");
        put(ConsentType.LOCATION_BASED_SERVICE, false, null);
        insertConsent("LOCATION_BASED_SERVICE");
        assertThat(consents.count()).isEqualTo(2);
    }

    private void insertConsent(String type) {
        jdbc.update("""
                INSERT INTO member_consent (id, member_id, consent_type, terms_version, agreed_at)
                VALUES (?, ?, ?, 'v1', ?)
                """, UUID.randomUUID(), fixture.memberId(), type, java.sql.Timestamp.from(clock.instant()));
    }

    private ConsentResponse put(ConsentType type, boolean agreed, String version) throws Exception {
        var response = request("PUT", "/" + type, new ConsentRequest(agreed, version), fixture);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readValue(response.body(), ConsentResponse.class);
    }

    private List<ConsentResponse> list(Fixture session) throws Exception {
        var response = request("GET", "", null, session);
        assertThat(response.statusCode()).isEqualTo(200);
        return Arrays.asList(mapper.readValue(response.body(), ConsentResponse[].class));
    }

    private HttpResponse<String> request(String method, String suffix, ConsentRequest body, Fixture session)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/consents" + suffix))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (session != null) {
            builder.header("Authorization", "Bearer " + session.token());
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    private Fixture fixture() {
        UUID memberId = members.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
        String token = UUID.randomUUID().toString();
        devices.save(Device.create(memberId, hasher.hash(token), NOW.plusSeconds(3600), NOW));
        return new Fixture(memberId, token);
    }

    private void awaitBlockedRequests(int holderPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int blocked = 0;
        while (System.nanoTime() < deadline) {
            blocked = jdbc.queryForObject("""
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
            Thread.sleep(10);
        }
        assertThat(blocked).as("both HTTP requests must wait on the real PostgreSQL Member lock").isEqualTo(2);
    }

    private record Fixture(UUID memberId, String token) {
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock consentTestClock() {
            return new TestClock();
        }
    }
}
