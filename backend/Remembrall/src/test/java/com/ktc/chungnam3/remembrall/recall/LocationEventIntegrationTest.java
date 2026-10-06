package com.ktc.chungnam3.remembrall.recall;

import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecution;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventType;
import com.ktc.chungnam3.remembrall.recall.service.LocationEventService;
import com.ktc.chungnam3.remembrall.recall.service.RecallExecutionService;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key",
        "spring.ai.google.genai.embedding.api-key=test-api-key",
        "youtube.api.key=test-api-key",
        "kakao.app-id=1",
        "dataportal.api.key=test-api-key",
        "locationiq.api.key=test-api-key"
})
@Import(LocationEventIntegrationTest.ClockConfiguration.class)
class LocationEventIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberConsentService consents;

    @Autowired
    private ConsentProperties consentProperties;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TriggerRepository triggerRepository;

    @Autowired
    private RecallExecutionRepository executionRepository;

    @MockitoSpyBean
    private RecallExecutionService executionService;

    @Autowired
    private LocationEventService eventService;

    @Autowired
    private RecallProperties properties;

    @Autowired
    private SessionTokenHasher tokenHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private RecallAgent agent;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private TransactionTemplate transactions;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        reset(agent);
        reset(executionService);
        executionRepository.deleteAll();
        triggerRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();
        transactions = new TransactionTemplate(transactionManager);
        fixture = createFixture();
    }

    @ParameterizedTest
    @CsvSource({"ENTER, false", "DWELL, true"})
    void commitsPendingBeforeRealStubCompletesNoAction(String eventType, boolean withLocation) throws Exception {
        AtomicInteger observations = new AtomicInteger();
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            try (ExecutorService reader = Executors.newSingleThreadExecutor()) {
                assertThat(reader.submit(() -> executionRepository.findById(id).orElseThrow().getStatus())
                        .get(10, TimeUnit.SECONDS)).isEqualTo(RecallExecutionStatus.PENDING);
            }
            return invocation.callRealMethod();
        }).when(executionService).start(any());
        doAnswer(invocation -> {
            UUID executionId = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            try (ExecutorService reader = Executors.newSingleThreadExecutor()) {
                RecallExecution committed = reader.submit(() -> executionRepository.findById(executionId).orElseThrow())
                        .get(10, TimeUnit.SECONDS);
                assertThat(committed.getStatus()).isEqualTo(RecallExecutionStatus.RUNNING);
                assertThat(committed.getResult()).isNull();
                assertThat(committed.getStartedAt()).isEqualTo(NOW);
            }
            observations.incrementAndGet();
            return invocation.callRealMethod();
        }).when(agent).execute(any(), any());
        UUID eventId = UUID.randomUUID();

        assertAccepted(post(fixture, body(fixture.placeId(), eventId, NOW, eventType,
                withLocation ? fullLocation() : null)));

        RecallExecution execution = executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)
                .orElseThrow();
        assertThat(observations.get()).isEqualTo(1);
        assertThat(execution.getTriggerId()).isEqualTo(fixture.triggerId());
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.COMPLETED);
        assertThat(execution.getResult()).isEqualTo(RecallExecutionResult.NO_ACTION);
        assertThat(execution.getAgentVersion()).isEqualTo("stub-v1");
        assertThat(execution.getEventOccurredAt()).isEqualTo(NOW);
        assertThat(execution.getStartedAt()).isEqualTo(NOW);
        assertThat(execution.getFinishedAt()).isEqualTo(NOW);
        assertThat(execution.getCreatedAt()).isEqualTo(NOW);
        assertThat(execution.getUpdatedAt()).isEqualTo(NOW);
        assertThat(execution.getFailureCode()).isNull();
        assertThat(execution.getProposalType()).isNull();
        assertThat(execution.getProposalPayload()).isNull();
        assertThat(execution.getDecisionSummary()).isNull();
        assertThat(properties.maxEventAge()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.memberCooldown()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void suspendsCallerTransactionAndRunsAgentOnlyAfterIndependentCommit() throws Exception {
        AtomicInteger observations = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            UUID id = invocation.getArgument(0);
            try (ExecutorService reader = Executors.newSingleThreadExecutor()) {
                assertThat(reader.submit(() -> executionRepository.findById(id).orElseThrow().getStatus())
                        .get(10, TimeUnit.SECONDS)).isEqualTo(RecallExecutionStatus.RUNNING);
            }
            observations.incrementAndGet();
            return invocation.callRealMethod();
        }).when(agent).execute(any(), any());
        UUID eventId = UUID.randomUUID();
        var event = new LocationEventRequest(fixture.placeId(), eventId, LocationEventType.ENTER, NOW, null);

        transactions.executeWithoutResult(status -> {
            eventService.accept(fixture.memberId(), event);
            status.setRollbackOnly();
        });

        assertThat(observations.get()).isEqualTo(1);
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)).get()
                .extracting(RecallExecution::getStatus).isEqualTo(RecallExecutionStatus.COMPLETED);
    }

    @Test
    void missingOrAnotherMembersTriggerReturns202WithoutExecution() throws Exception {
        Fixture other = createFixture();

        assertAccepted(post(fixture, body(UUID.randomUUID(), UUID.randomUUID(), NOW, "ENTER", null)));
        assertAccepted(post(fixture, body(other.placeId(), UUID.randomUUID(), NOW, "DWELL", null)));

        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);
    }

    @Test
    void duplicateEventIdCreatesOnlyOneExecutionEvenAfterMemberCooldown() throws Exception {
        UUID eventId = UUID.randomUUID();
        String event = body(fixture.placeId(), eventId, NOW, "ENTER", null);
        assertAccepted(post(fixture, event));
        jdbcTemplate.update("UPDATE recall_execution SET created_at = ? WHERE trigger_id = ? AND trigger_event_id = ?",
                java.sql.Timestamp.from(NOW.minus(Duration.ofMinutes(11))), fixture.triggerId(), eventId);

        assertAccepted(post(fixture, event));

        assertThat(executionRepository.count()).isOne();
        verify(agent, times(1)).execute(any(), any());
    }

    @Test
    void sameEventIdAcrossMembersCreatesExecutionForEachMemberAtSamePlace() throws Exception {
        Fixture other = createFixture(fixture.placeId());
        UUID eventId = UUID.randomUUID();

        assertAccepted(post(fixture, body(fixture.placeId(), eventId, NOW, "ENTER", null)));
        assertAccepted(post(other, body(other.placeId(), eventId, NOW, "DWELL", null)));

        assertThat(executionRepository.count()).isEqualTo(2);
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)).isPresent();
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(other.triggerId(), eventId)).isPresent();
        verify(agent, times(2)).execute(any(), any());
    }

    @Test
    void olderThan30MinutesReturns202WithoutExecution() throws Exception {
        assertAccepted(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW.minusSeconds(1801), "ENTER", null)));
        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);
    }

    @Test
    void exactly30MinutesOldIsAccepted() throws Exception {
        assertAccepted(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW.minusSeconds(1800), "ENTER", null)));
        assertThat(executionRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "withdrawn", "publicOnly"})
    void locationConsentIsRequiredBeforeEventCanCreateExecution(String state) throws Exception {
        consents.change(fixture.memberId(), ConsentType.LOCATION_BASED_SERVICE, new ConsentRequest(false, null));
        if (!"withdrawn".equals(state)) {
            jdbcTemplate.update("DELETE FROM member_consent WHERE member_id = ?", fixture.memberId());
        }
        if ("publicOnly".equals(state)) {
            consents.change(fixture.memberId(), ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION,
                    new ConsentRequest(true, consentProperties.currentVersion(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION)));
        }
        UUID eventId = UUID.randomUUID();
        String event = body(fixture.placeId(), eventId, NOW, "ENTER", null);

        assertAccepted(post(fixture, event));
        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);

        consents.change(fixture.memberId(), ConsentType.LOCATION_BASED_SERVICE,
                new ConsentRequest(true, consentProperties.currentVersion(ConsentType.LOCATION_BASED_SERVICE)));
        assertAccepted(post(fixture, event));
        assertThat(executionRepository.count()).isOne();
        verify(agent, times(1)).execute(any(), any());
    }

    @ParameterizedTest
    @EnumSource(RecallExecutionStatus.class)
    void memberCooldownBlocksRepeatEventsAtSamePlaceForEveryStatus(RecallExecutionStatus status) throws Exception {
        UUID eventId = UUID.randomUUID();
        assertAccepted(post(fixture, body(fixture.placeId(), eventId, NOW.minusSeconds(1200), "ENTER", null)));
        if (status != RecallExecutionStatus.COMPLETED) {
            jdbcTemplate.update("UPDATE recall_execution SET status = ?, result = NULL WHERE trigger_id = ? AND trigger_event_id = ?",
                    status.name(), fixture.triggerId(), eventId);
        }
        clearInvocations(agent);

        assertAccepted(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, "DWELL", null)));

        assertThat(executionRepository.count()).isOne();
        verifyNoInteractions(agent);
    }

    @Test
    void acceptsNewEventAtMemberCooldownBoundary() throws Exception {
        UUID previousId = UUID.randomUUID();
        assertAccepted(post(fixture, body(fixture.placeId(), previousId, NOW, "ENTER", null)));
        jdbcTemplate.update("UPDATE recall_execution SET created_at = ? WHERE trigger_id = ? AND trigger_event_id = ?",
                java.sql.Timestamp.from(NOW.minus(properties.memberCooldown())), fixture.triggerId(), previousId);

        assertAccepted(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, "DWELL", null)));

        assertThat(executionRepository.count()).isEqualTo(2);
    }

    @Test
    void simultaneousDifferentEventsForSameTriggerCreateOnlyOneExecution() throws Exception {
        List<HttpResponse<String>> responses = postConcurrently(List.of(fixture, fixture),
                List.of(UUID.randomUUID(), UUID.randomUUID()));

        responses.forEach(this::assertAccepted);
        assertThat(executionRepository.count()).isOne();
        verify(agent, times(1)).execute(any(), any());
    }

    @Test
    void simultaneousSameEventForSameTriggerCreatesOnlyOneExecution() throws Exception {
        UUID eventId = UUID.randomUUID();
        List<HttpResponse<String>> responses = postConcurrently(List.of(fixture, fixture), List.of(eventId, eventId));

        responses.forEach(this::assertAccepted);
        assertThat(executionRepository.count()).isOne();
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)).isPresent();
        verify(agent, times(1)).execute(any(), any());
    }

    @Test
    void simultaneousDifferentTriggersForSameMemberCreateOnlyOneExecution() throws Exception {
        Fixture other = anotherTriggerFor(fixture);
        postConcurrently(List.of(fixture, other), List.of(UUID.randomUUID(), UUID.randomUUID()))
                .forEach(this::assertAccepted);
        assertThat(executionRepository.count()).isOne();
        verify(agent, times(1)).execute(any(), any());
    }

    @ParameterizedTest
    @EnumSource(RecallExecutionStatus.class)
    void memberCooldownIncludesAllExecutionStatesAcrossTriggers(RecallExecutionStatus status) throws Exception {
        UUID eventId = UUID.randomUUID();
        assertAccepted(post(fixture, body(fixture.placeId(), eventId, NOW, "ENTER", null)));
        jdbcTemplate.update("UPDATE recall_execution SET status = ? WHERE trigger_id = ? AND trigger_event_id = ?",
                status.name(), fixture.triggerId(), eventId);
        Fixture other = anotherTriggerFor(fixture);

        assertAccepted(post(other, body(other.placeId(), UUID.randomUUID(), NOW, "ENTER", null)));

        assertThat(executionRepository.count()).isOne();
        verify(agent, times(1)).execute(any(), any());
    }

    @Test
    void anotherTriggerIsAllowedAfterMemberCooldown() throws Exception {
        assertAccepted(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, "ENTER", null)));
        jdbcTemplate.update("UPDATE recall_execution SET created_at = ? WHERE trigger_id = ?",
                java.sql.Timestamp.from(NOW.minus(Duration.ofMinutes(11))), fixture.triggerId());
        Fixture other = anotherTriggerFor(fixture);
        assertAccepted(post(other, body(other.placeId(), UUID.randomUUID(), NOW, "ENTER", null)));
        assertThat(executionRepository.count()).isEqualTo(2);
        verify(agent, times(2)).execute(any(), any());
    }

    @Test
    void simultaneousSameEventForDifferentTriggersCreatesExecutionForEach() throws Exception {
        Fixture other = createFixture(fixture.placeId());
        UUID eventId = UUID.randomUUID();
        List<HttpResponse<String>> responses = postConcurrently(List.of(fixture, other), List.of(eventId, eventId));

        responses.forEach(this::assertAccepted);
        assertThat(executionRepository.count()).isEqualTo(2);
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)).isPresent();
        assertThat(executionRepository.findByTriggerIdAndTriggerEventId(other.triggerId(), eventId)).isPresent();
        verify(agent, times(2)).execute(any(), any());
    }

    @Test
    void agentFailureFromRunningIsRecordedAndApiStillReturns202() throws Exception {
        doAnswer(invocation -> {
            assertThat(executionRepository.findById(invocation.getArgument(0)).orElseThrow().getStatus())
                    .isEqualTo(RecallExecutionStatus.RUNNING);
            throw new IllegalStateException("Test agent failure");
        }).when(agent).execute(any(), any());
        UUID eventId = UUID.randomUUID();

        assertAccepted(post(fixture, body(fixture.placeId(), eventId, NOW, "ENTER", null)));

        RecallExecution execution = executionRepository.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId)
                .orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.FAILED);
        assertThat(execution.getFailureCode()).isEqualTo("AGENT_EXECUTION_ERROR");
        assertThat(execution.getResult()).isNull();
        assertThat(execution.getFinishedAt()).isEqualTo(NOW);
        assertThat(execution.getStartedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXIT", "enter", "0"})
    void rejectsEventTypeOtherThanEnterOrDwell(String eventType) throws Exception {
        assertError(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, eventType, null)),
                400, ErrorCode.INVALID_REQUEST);
        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);
    }

    @Test
    void rejectsNumericEnumOrdinal() throws Exception {
        assertError(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, 0, null)),
                400, ErrorCode.INVALID_REQUEST);
        assertThat(executionRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude", "accuracyMeters", "locatedAt"})
    void rejectsLocationWithAnyFieldMissing(String missingField) throws Exception {
        Map<String, Object> location = fullLocation();
        location.remove(missingField);

        assertError(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, "ENTER", location)),
                400, ErrorCode.INVALID_REQUEST);
        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);
    }

    @Test
    void rejectsEmptyLocationObject() throws Exception {
        assertError(post(fixture, body(fixture.placeId(), UUID.randomUUID(), NOW, "ENTER", Map.of())),
                400, ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"placeId", "triggerEventId", "eventType", "eventOccurredAt"})
    void rejectsRequestWithAnyRequiredFieldMissing(String missingField) throws Exception {
        Map<String, Object> request = eventFields(fixture.placeId(), UUID.randomUUID(), NOW, "ENTER", null);
        request.remove(missingField);

        assertError(post(fixture, objectMapper.writeValueAsString(request)), 400, ErrorCode.INVALID_REQUEST);
        assertThat(executionRepository.count()).isZero();
    }

    @Test
    void requestWithoutSessionReturns401() throws Exception {
        assertError(post(null, body(fixture.placeId(), UUID.randomUUID(), NOW, "ENTER", null)),
                401, ErrorCode.INVALID_SESSION);
        assertThat(executionRepository.count()).isZero();
        verifyNoInteractions(agent);
    }

    @Test
    void conditionalTransitionsNeverOverwriteCompletedOrSkipPending() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(status -> executionRepository.insertPendingIfAbsent(
                id, fixture.triggerId(), eventId, "stub-v1", NOW, NOW));

        assertThat(executionService.completeNoAction(id)).isFalse();
        assertThat(executionService.start(id)).isTrue();
        assertThat(executionService.start(id)).isFalse();
        assertThat(executionService.completeNoAction(id)).isTrue();
        assertThat(executionService.completeNoAction(id)).isFalse();
        executionService.fail(id, "SHOULD_NOT_OVERWRITE");
        assertThat(executionRepository.findById(id)).get()
                .extracting(RecallExecution::getStatus, RecallExecution::getResult, RecallExecution::getFailureCode)
                .containsExactly(RecallExecutionStatus.COMPLETED, RecallExecutionResult.NO_ACTION, null);
    }

    private Fixture createFixture() {
        return createFixture(null);
    }

    private Fixture anotherTriggerFor(Fixture member) {
        UUID placeId = placeRepository.save(Place.create("Other place", "Address", 36.35, 127.38,
                GeocodingProvider.LOCATIONIQ, UUID.randomUUID().toString(), VerificationProvider.KAKAO,
                UUID.randomUUID().toString(), NOW, NOW)).getId();
        UUID triggerId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> triggerRepository.insertIfAbsent(
                triggerId, member.memberId(), placeId, NOW));
        return new Fixture(member.memberId(), placeId, triggerId, member.token());
    }

    private Fixture createFixture(UUID existingPlaceId) {
        UUID memberId = memberRepository.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
        String token = UUID.randomUUID().toString();
        deviceRepository.save(Device.create(memberId, tokenHasher.hash(token), NOW.plusSeconds(3600), NOW));
        UUID placeId = existingPlaceId != null ? existingPlaceId : placeRepository.save(Place.create(
                "Place", "Address", 36.35, 127.38, GeocodingProvider.LOCATIONIQ, UUID.randomUUID().toString(),
                VerificationProvider.KAKAO, UUID.randomUUID().toString(), NOW, NOW)).getId();
        UUID triggerId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> triggerRepository.insertIfAbsent(triggerId, memberId, placeId, NOW));
        consents.change(memberId, ConsentType.LOCATION_BASED_SERVICE,
                new ConsentRequest(true, consentProperties.currentVersion(ConsentType.LOCATION_BASED_SERVICE)));
        return new Fixture(memberId, placeId, triggerId, token);
    }

    private Map<String, Object> fullLocation() {
        return new LinkedHashMap<>(Map.of("latitude", 36.35, "longitude", 127.38,
                "accuracyMeters", 10.0, "locatedAt", NOW.toString()));
    }

    private String body(UUID placeId, UUID eventId, Instant occurredAt, Object type, Object location) {
        return objectMapper.writeValueAsString(eventFields(placeId, eventId, occurredAt, type, location));
    }

    private Map<String, Object> eventFields(UUID placeId, UUID eventId, Instant occurredAt, Object type, Object location) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("placeId", placeId, "triggerEventId", eventId,
                "eventType", type, "eventOccurredAt", occurredAt.toString()));
        if (location != null) {
            body.put("location", location);
        }
        return body;
    }

    private HttpResponse<String> post(Fixture session, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/location-events"))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertAccepted(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(objectMapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    private List<HttpResponse<String>> postConcurrently(List<Fixture> sessions, List<UUID> eventIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        try {
            transactions.executeWithoutResult(status -> {
                sessions.stream().map(Fixture::memberId).distinct().sorted()
                        .forEach(memberId -> memberRepository.findByIdForUpdate(memberId).orElseThrow());
                Integer holderPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                for (int index = 0; index < 2; index++) {
                    Fixture session = sessions.get(index);
                    String request = body(session.placeId(), eventIds.get(index), NOW, "ENTER", null);
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        return post(session, request);
                    }));
                }
                try {
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                start.countDown();
                awaitBlockedRequests(holderPid);
            });
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedRequests(int holderPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int blocked = 0;
        while (System.nanoTime() < deadline) {
            blocked = jdbcTemplate.queryForObject("""
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
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        assertThat(blocked).as("both HTTP requests must wait on real PostgreSQL Member locks").isEqualTo(2);
    }

    private record Fixture(UUID memberId, UUID placeId, UUID triggerId, String token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        Clock recallTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
