package com.ktc.chungnam3.remembrall.notification;

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
import com.ktc.chungnam3.remembrall.domain.notification.NotificationStatus;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import com.ktc.chungnam3.remembrall.notification.dto.NotificationDetail;
import com.ktc.chungnam3.remembrall.notification.push.PushMessage;
import com.ktc.chungnam3.remembrall.notification.push.PushSendException;
import com.ktc.chungnam3.remembrall.notification.push.PushSender;
import com.ktc.chungnam3.remembrall.notification.service.NotificationDeliveryService;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalPayload;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventType;
import com.ktc.chungnam3.remembrall.recall.service.RecallExecutionService;
import com.ktc.chungnam3.remembrall.recall.service.ProposalValidator;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.NotificationRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key",
        "youtube.api.key=test-api-key", "kakao.app-id=1",
        "dataportal.api.key=test-api-key", "locationiq.api.key=test-api-key"
})
@Import(NotificationIntegrationTest.ClockConfiguration.class)
class NotificationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private MemberRepository members;
    @Autowired private MemberConsentService consents;
    @Autowired private ConsentProperties consentProperties;
    @Autowired private DeviceRepository devices;
    @Autowired private PlaceRepository places;
    @Autowired private TriggerRepository triggers;
    @Autowired private RecallExecutionRepository executions;
    @Autowired private NotificationRepository notifications;
    @Autowired private ContentRepository contents;
    @Autowired private PersonalSaveRepository saves;
    @Autowired private ContentPlaceRepository links;
    @Autowired private RecallExecutionService executionService;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private RecallAgent agent;
    @MockitoSpyBean private NotificationDeliveryService delivery;
    @MockitoSpyBean private ProposalValidator validator;
    @MockitoBean private PushSender sender;
    @LocalServerPort private int port;

    private Fixture fixture;
    private TransactionTemplate transactions;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        reset(agent, sender, delivery, validator);
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        transactions = new TransactionTemplate(transactionManager);
        fixture = fixture();
        when(sender.send(any())).thenReturn("projects/test/messages/123");
    }

    @Test
    void proposesCommitsPendingThenSendsExpectedMessageOutsideTransaction() throws Exception {
        choose(proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId())));
        AtomicReference<UUID> pendingId = new AtomicReference<>();
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            try (var reader = Executors.newSingleThreadExecutor()) {
                var pending = reader.submit(() -> notifications.findById(id).orElseThrow()).get(10, TimeUnit.SECONDS);
                assertThat(pending.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(pending.getMemberId()).isEqualTo(fixture.memberId());
                assertThat(reader.submit(() -> executions.findById(pending.getExecutionId()).orElseThrow().getResult())
                        .get(10, TimeUnit.SECONDS))
                        .isEqualTo(RecallExecutionResult.PROPOSE);
            }
            pendingId.set(id);
            return invocation.callRealMethod();
        }).when(delivery).send(any());
        when(sender.send(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var pending = notifications.findById(pendingId.get()).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(NotificationStatus.SENDING);
            PushMessage message = invocation.getArgument(0);
            assertThat(message.token()).isEqualTo(fixture.fcmToken());
            assertThat(message.title()).isEqualTo("Agent title");
            assertThat(message.body()).isEqualTo("Agent body");
            assertThat(message.data()).containsExactlyEntriesOf(Map.of("notificationId", pendingId.get().toString()));
            assertThat(message.androidTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(message.androidTag()).isEqualTo(pendingId.get().toString());
            assertThat(message.apnsHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "apns-expiration", Long.toString(NOW.plusSeconds(1800).getEpochSecond()),
                    "apns-collapse-id", pendingId.get().toString()));
            return "projects/test/messages/123";
        });

        UUID executionId = accept();

        var notification = notifications.findByExecutionId(executionId).orElseThrow();
        assertThat(notification.getId()).isEqualTo(pendingId.get());
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getProviderMessageId()).isEqualTo("projects/test/messages/123");
        assertThat(notification.getSentAt()).isEqualTo(NOW);
        assertThat(notification.getFailureCode()).isNull();
        var payload = executions.findById(executionId).orElseThrow().getProposalPayload();
        assertThat(payload).containsOnlyKeys("items");
        assertThat(((Map<?, ?>)((List<?>) payload.get("items")).getFirst()).keySet()).extracting(Object::toString)
                .containsExactlyInAnyOrder("contentId", "placeId", "source");
    }

    @Test
    void noActionDoesNotCreateNotification() throws Exception {
        UUID id = accept();
        assertThat(executions.findById(id).orElseThrow().getResult()).isEqualTo(RecallExecutionResult.NO_ACTION);
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @EnumSource(value = RecallExecutionStatus.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void completionFromWrongStateDoesNotCreateNotification(RecallExecutionStatus status) {
        UUID id = pending();
        jdbc.update("UPDATE recall_execution SET status = ? WHERE id = ?", status.name(), id);
        assertThat(executionService.complete(id, proposal(ProposalType.PLACE,
                item(fixture.contentId(), fixture.placeId())))).isNull();
        assertThat(notifications.count()).isZero();
        assertThat(executions.findById(id).orElseThrow().getStatus()).isEqualTo(status);
    }

    @Test
    void completionAndSendingAreBothIdempotent() throws Exception {
        var result = proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId()));
        UUID id = pending();
        assertThat(executionService.start(id)).isTrue();
        UUID notificationId = executionService.complete(id, result);
        assertThat(notificationId).isNotNull();
        assertThat(executionService.complete(id, result)).isNull();
        assertThat(notifications.count()).isOne();
        delivery.send(notificationId);
        delivery.send(notificationId);
        verify(sender, times(1)).send(any());
    }

    @Test
    void concurrentCompletionsCreateOneNotification() throws Exception {
        UUID id = pending();
        executionService.start(id);
        var result = proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId()));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            var task = (java.util.concurrent.Callable<UUID>) () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return executionService.complete(id, result);
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .filteredOn(java.util.Objects::nonNull).hasSize(1);
        }
        assertThat(notifications.count()).isOne();
    }

    @Test
    void stateChangeAfterValidationMakesCompletionUpdateFailWithoutNotification() {
        UUID id = pending();
        executionService.start(id);
        doAnswer(invocation -> {
            boolean valid = (boolean) invocation.callRealMethod();
            executionService.fail(id, "TEST_STATE_CHANGE");
            return valid;
        }).when(validator).valid(any(), any());

        assertThat(executionService.complete(id, proposal(ProposalType.PLACE,
                item(fixture.contentId(), fixture.placeId())))).isNull();

        assertThat(notifications.count()).isZero();
        var execution = executions.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.FAILED);
        assertThat(execution.getFailureCode()).isEqualTo("TEST_STATE_CHANGE");
        assertThat(execution.getResult()).isNull();
    }

    @Test
    void notificationInsertFailureRollsBackExecutionCompletion() {
        UUID id = pending();
        executionService.start(id);
        transactions.executeWithoutResult(status -> notifications.insertPending(UUID.randomUUID(), id,
                fixture.memberId(), "Existing", "Existing", NOW));

        assertThatThrownBy(() -> executionService.complete(id, proposal(ProposalType.PLACE,
                item(fixture.contentId(), fixture.placeId()))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        var execution = executions.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.RUNNING);
        assertThat(execution.getResult()).isNull();
        assertThat(execution.getProposalPayload()).isNull();
        assertThat(notifications.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingContent", "missingPlace", "unlinkedPlace", "otherMember", "PUBLIC",
            "placeMissingPlace", "placeTwoItems", "contentWithPlace", "courseOneItem", "courseMissingPlace",
            "nullPayload", "nullItems", "nullItem", "nullSource", "nullType", "emptyItems"})
    void invalidPayloadFailsExecutionWithoutNotification(String invalid) throws Exception {
        var valid = item(fixture.contentId(), fixture.placeId());
        RecallAgentResult result = switch (invalid) {
            case "missingContent" -> proposal(ProposalType.PLACE, item(UUID.randomUUID(), fixture.placeId()));
            case "missingPlace" -> proposal(ProposalType.PLACE, item(fixture.contentId(), UUID.randomUUID()));
            case "unlinkedPlace" -> proposal(ProposalType.PLACE, item(fixture.contentId(), fixture().placeId()));
            case "otherMember" -> {
                var other = fixture();
                yield proposal(ProposalType.PLACE, item(other.contentId(), other.placeId()));
            }
            case "PUBLIC" -> proposal(ProposalType.PLACE, new ProposalPayload.Item(
                    fixture.contentId(), fixture.placeId(), ProposalSource.PUBLIC));
            case "placeMissingPlace" -> proposal(ProposalType.PLACE, item(fixture.contentId(), null));
            case "placeTwoItems" -> proposal(ProposalType.PLACE, valid, valid);
            case "contentWithPlace" -> proposal(ProposalType.CONTENT, valid);
            case "courseOneItem" -> proposal(ProposalType.COURSE, valid);
            case "courseMissingPlace" -> proposal(ProposalType.COURSE, valid, item(fixture.contentId(), null));
            case "nullPayload" -> RecallAgentResult.propose(ProposalType.PLACE, null, "why", "title", "body");
            case "nullItems" -> RecallAgentResult.propose(ProposalType.PLACE,
                    new ProposalPayload(null), "why", "title", "body");
            case "nullItem" -> proposal(ProposalType.PLACE, (ProposalPayload.Item) null);
            case "nullSource" -> proposal(ProposalType.PLACE,
                    new ProposalPayload.Item(fixture.contentId(), fixture.placeId(), null));
            case "nullType" -> proposal(null, valid);
            default -> proposal(ProposalType.PLACE);
        };
        doReturn(result).when(agent).execute(any(), any());
        UUID id = accept();
        var execution = executions.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.FAILED);
        assertThat(execution.getFailureCode()).isEqualTo("INVALID_PROPOSAL");
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "expired", "inactive"})
    void missingOrInactiveTokenFailsWithoutSending(String state) throws Exception {
        UUID id = pending();
        executionService.start(id);
        UUID notificationId = executionService.complete(id,
                proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId())));
        switch (state) {
            case "null" -> jdbc.update("UPDATE device SET fcm_token = NULL WHERE id = ?", fixture.deviceId());
            case "expired" -> jdbc.update("UPDATE device SET session_expires_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(NOW), fixture.deviceId());
            default -> jdbc.update("UPDATE device SET session_token_hash = NULL WHERE id = ?", fixture.deviceId());
        }
        delivery.send(notificationId);
        var notification = notifications.findById(notificationId).orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureCode()).isEqualTo("NO_FCM_TOKEN");
        assertThat(notification.getSentAt()).isNull();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicateCoursePlacesFailEvenWhenContentsDiffer(boolean differentContents) throws Exception {
        UUID secondContentId = differentContents ? UUID.randomUUID() : fixture.contentId();
        if (differentContents) {
            transactions.executeWithoutResult(status -> {
                contents.insertIfAbsent(secondContentId, UUID.randomUUID().toString().substring(0, 20), NOW);
                saves.insertIfAbsent(UUID.randomUUID(), fixture.memberId(), secondContentId, NOW);
                links.insertIfAbsent(UUID.randomUUID(), secondContentId, fixture.placeId(), "Same place", NOW);
            });
        }
        choose(proposal(ProposalType.COURSE, item(fixture.contentId(), fixture.placeId()),
                item(secondContentId, fixture.placeId())));

        UUID id = accept();

        var execution = executions.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.FAILED);
        assertThat(execution.getFailureCode()).isEqualTo("INVALID_PROPOSAL");
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNREGISTERED", "INVALID_ARGUMENT", "UNAVAILABLE"})
    void pushFailureOnlyClearsUnregisteredToken(String code) throws Exception {
        choose(proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId())));
        when(sender.send(any())).thenThrow(new PushSendException(code));
        UUID id = accept();
        var notification = notifications.findByExecutionId(id).orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureCode()).isEqualTo(code);
        assertThat(notification.getSentAt()).isNull();
        assertThat(devices.findById(fixture.deviceId()).orElseThrow().getFcmToken())
                .isEqualTo("UNREGISTERED".equals(code) ? null : fixture.fcmToken());
        assertThat(executions.findById(id).orElseThrow().getStatus()).isEqualTo(RecallExecutionStatus.COMPLETED);
    }

    @Test
    void unregisteredDoesNotClearReplacementToken() throws Exception {
        choose(proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId())));
        when(sender.send(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("UPDATE device SET fcm_token = 'replacement' WHERE id = ?", fixture.deviceId());
            throw new PushSendException("UNREGISTERED");
        });
        UUID id = accept();
        assertThat(notifications.findByExecutionId(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(devices.findById(fixture.deviceId()).orElseThrow().getFcmToken()).isEqualTo("replacement");
    }

    @Test
    void truncatesTitleAndBodyWithoutSplittingUnicode() throws Exception {
        choose(RecallAgentResult.propose(ProposalType.PLACE,
                new ProposalPayload(List.of(item(fixture.contentId(), fixture.placeId()))),
                "why", "😀".repeat(121), "😀".repeat(201)));
        var notification = notifications.findByExecutionId(accept()).orElseThrow();
        assertThat(notification.getTitle()).isEqualTo("😀".repeat(120));
        assertThat(notification.getBody()).isEqualTo("😀".repeat(200));
        var captured = org.mockito.ArgumentCaptor.forClass(PushMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().title()).isEqualTo(notification.getTitle());
        assertThat(captured.getValue().body()).isEqualTo(notification.getBody());
    }

    @Test
    void openingIsOwnedAndIdempotentAndRequiresSession() throws Exception {
        UUID id = sentNotification();
        assertThat(notifications.findById(id).orElseThrow().getOpenedAt()).isNull();
        assertError(request("POST", id + "/open", fixture()), 404, ErrorCode.NOT_FOUND);
        assertThat(notifications.findById(id).orElseThrow().getOpenedAt()).isNull();
        assertThat(request("POST", id + "/open", fixture).statusCode()).isEqualTo(204);
        assertThat(notifications.findById(id).orElseThrow().getOpenedAt()).isEqualTo(NOW);
        Instant earlier = NOW.minusSeconds(60);
        jdbc.update("UPDATE notification SET opened_at = ? WHERE id = ?", java.sql.Timestamp.from(earlier), id);
        assertThat(request("POST", id + "/open", fixture).statusCode()).isEqualTo(204);
        assertThat(notifications.findById(id).orElseThrow().getOpenedAt()).isEqualTo(earlier);
        assertError(request("POST", UUID.randomUUID() + "/open", fixture), 404, ErrorCode.NOT_FOUND);
        assertError(request("POST", id + "/open", null), 401, ErrorCode.INVALID_SESSION);
    }

    @Test
    void detailIsOwnedAndResolvesCurrentSaveContentAndPlace() throws Exception {
        UUID id = sentNotification();
        var detail = detail(id);
        assertThat(detail.notificationId()).isEqualTo(id);
        assertThat(detail.title()).isEqualTo("Agent title");
        assertThat(detail.body()).isEqualTo("Agent body");
        assertThat(detail.proposalType()).isEqualTo(ProposalType.PLACE);
        assertThat(detail.sentAt()).isEqualTo(NOW);
        assertThat(detail.openedAt()).isNull();
        assertThat(detail.items()).containsExactly(new NotificationDetail.Item(fixture.contentId(),
                ProposalSource.PERSONAL, fixture.saveId(), "Content title", "Summary",
                new NotificationDetail.Place(fixture.placeId(), "Place", "Address", 36.35, 127.38)));
        jdbc.update("UPDATE content SET title = 'New title', summary = 'New summary' WHERE id = ?", fixture.contentId());
        jdbc.update("UPDATE place SET name = 'New place' WHERE id = ?", fixture.placeId());
        assertThat(detail(id).items().getFirst().title()).isEqualTo("New title");
        assertThat(detail(id).items().getFirst().summary()).isEqualTo("New summary");
        assertThat(detail(id).items().getFirst().place().name()).isEqualTo("New place");
        assertError(request("GET", id.toString(), fixture()), 404, ErrorCode.NOT_FOUND);
        assertError(request("GET", UUID.randomUUID().toString(), fixture), 404, ErrorCode.NOT_FOUND);
        assertError(request("GET", id.toString(), null), 401, ErrorCode.INVALID_SESSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "content", "link", "place"})
    void detailOmitsDeletedReferences(String deletion) throws Exception {
        Fixture other = fixture();
        transactions.executeWithoutResult(status -> saves.insertIfAbsent(UUID.randomUUID(), fixture.memberId(),
                other.contentId(), NOW));
        choose(proposal(ProposalType.COURSE, item(fixture.contentId(), fixture.placeId()),
                item(other.contentId(), other.placeId())));
        UUID id = notifications.findByExecutionId(accept()).orElseThrow().getId();
        switch (deletion) {
            case "save" -> jdbc.update("DELETE FROM personal_save WHERE member_id = ? AND content_id = ?",
                    fixture.memberId(), other.contentId());
            case "content" -> jdbc.update("DELETE FROM content WHERE id = ?", other.contentId());
            case "link" -> jdbc.update("DELETE FROM content_place WHERE content_id = ?", other.contentId());
            default -> jdbc.update("DELETE FROM place WHERE id = ?", other.placeId());
        }
        assertThat(detail(id).items()).extracting(NotificationDetail.Item::contentId).containsExactly(fixture.contentId());
        jdbc.update("DELETE FROM personal_save WHERE id = ?", fixture.saveId());
        assertThat(detail(id).items()).isEmpty();
    }

    @Test
    void contentProposalHasNullPlaceAndCourseRetainsOrder() throws Exception {
        choose(proposal(ProposalType.CONTENT, item(fixture.contentId(), null)));
        UUID contentNotification = notifications.findByExecutionId(accept()).orElseThrow().getId();
        assertThat(detail(contentNotification).proposalType()).isEqualTo(ProposalType.CONTENT);
        assertThat(detail(contentNotification).items().getFirst().place()).isNull();
        Fixture other = fixture();
        transactions.executeWithoutResult(status -> saves.insertIfAbsent(UUID.randomUUID(), fixture.memberId(),
                other.contentId(), NOW));
        UUID id = pending();
        executionService.start(id);
        UUID notificationId = executionService.complete(id, proposal(ProposalType.COURSE,
                item(other.contentId(), other.placeId()), item(fixture.contentId(), fixture.placeId())));
        assertThat(detail(notificationId).items()).extracting(NotificationDetail.Item::contentId)
                .containsExactly(other.contentId(), fixture.contentId());
    }

    @Test
    void notificationSchemaHasOnlyRequiredIndexesAndCascades() throws Exception {
        UUID id = sentNotification();
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'notification'
                ORDER BY ordinal_position
                """, String.class)).containsExactly("id", "execution_id", "member_id", "title", "body", "status",
                "provider_message_id", "failure_code", "sent_at", "opened_at", "created_at", "updated_at");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'notification'", String.class))
                .containsExactlyInAnyOrder("notification_pkey", "uk_notification_execution");
        jdbc.update("DELETE FROM recall_execution WHERE id = ?", notifications.findById(id).orElseThrow().getExecutionId());
        assertThat(notifications.existsById(id)).isFalse();
        UUID executionId = pending();
        executionService.start(executionId);
        UUID second = executionService.complete(executionId, proposal(ProposalType.PLACE,
                item(fixture.contentId(), fixture.placeId())));
        jdbc.update("DELETE FROM member WHERE id = ?", fixture.memberId());
        assertThat(notifications.existsById(second)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"status = 'UNKNOWN'", "sent_at = CURRENT_TIMESTAMP", "failure_code = 'BAD'",
            "status = 'SENT'", "status = 'FAILED'"})
    void databaseEnforcesStatusTimestampAndFailureChecks(String mutation) throws Exception {
        UUID executionId = pending();
        executionService.start(executionId);
        UUID id = executionService.complete(executionId, proposal(ProposalType.PLACE,
                item(fixture.contentId(), fixture.placeId())));
        assertThatThrownBy(() -> jdbc.update("UPDATE notification SET " + mutation + " WHERE id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void choose(RecallAgentResult result) throws Exception {
        StubRecallAgent stub = new StubRecallAgent(result);
        doAnswer(invocation -> stub.execute(invocation.getArgument(0), invocation.getArgument(1)))
                .when(agent).execute(any(), any());
    }

    private RecallAgentResult proposal(ProposalType type, ProposalPayload.Item... items) {
        return RecallAgentResult.propose(type, new ProposalPayload(Arrays.asList(items)),
                "Decision", "Agent title", "Agent body");
    }

    private ProposalPayload.Item item(UUID contentId, UUID placeId) {
        return new ProposalPayload.Item(contentId, placeId, ProposalSource.PERSONAL);
    }

    private UUID sentNotification() throws Exception {
        choose(proposal(ProposalType.PLACE, item(fixture.contentId(), fixture.placeId())));
        return notifications.findByExecutionId(accept()).orElseThrow().getId();
    }

    private UUID accept() throws Exception {
        UUID eventId = UUID.randomUUID();
        var event = new LocationEventRequest(fixture.placeId(), eventId, LocationEventType.ENTER, NOW, null);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/location-events"))
                .header("Authorization", "Bearer " + fixture.token()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(event))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
        return executions.findByTriggerIdAndTriggerEventId(fixture.triggerId(), eventId).orElseThrow().getId();
    }

    private UUID pending() {
        UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(status -> executions.insertPendingIfAbsent(id, fixture.triggerId(),
                UUID.randomUUID(), "stub-v1", NOW, NOW));
        return id;
    }

    private Fixture fixture() {
        UUID memberId = members.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
        String token = UUID.randomUUID().toString();
        String fcm = UUID.randomUUID().toString();
        Device device = Device.create(memberId, hasher.hash(token), NOW.plusSeconds(3600), NOW);
        device.updateFcmToken(fcm, NOW);
        UUID deviceId = devices.save(device).getId();
        UUID placeId = places.save(Place.create("Place", "Address", 36.35, 127.38,
                GeocodingProvider.LOCATIONIQ, UUID.randomUUID().toString(), VerificationProvider.KAKAO,
                UUID.randomUUID().toString(), NOW, NOW)).getId();
        UUID triggerId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID saveId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            triggers.insertIfAbsent(triggerId, memberId, placeId, NOW);
            contents.insertIfAbsent(contentId, UUID.randomUUID().toString().substring(0, 20), NOW);
            saves.insertIfAbsent(saveId, memberId, contentId, NOW);
            links.insertIfAbsent(UUID.randomUUID(), contentId, placeId, "Link", NOW);
        });
        jdbc.update("UPDATE content SET title = 'Content title', summary = 'Summary' WHERE id = ?", contentId);
        consents.change(memberId, ConsentType.LOCATION_BASED_SERVICE,
                new ConsentRequest(true, consentProperties.currentVersion(ConsentType.LOCATION_BASED_SERVICE)));
        return new Fixture(memberId, deviceId, token, fcm, placeId, triggerId, contentId, saveId);
    }

    private HttpResponse<String> request(String method, String suffix, Fixture session) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications/" + suffix))
                .method(method, HttpRequest.BodyPublishers.noBody()).header("Accept", "application/json");
        if (session != null) {
            builder.header("Authorization", "Bearer " + session.token());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private NotificationDetail detail(UUID id) throws Exception {
        var response = request("GET", id.toString(), fixture);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readValue(response.body(), NotificationDetail.class);
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    private record Fixture(UUID memberId, UUID deviceId, String token, String fcmToken, UUID placeId,
                           UUID triggerId, UUID contentId, UUID saveId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        Clock notificationTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
