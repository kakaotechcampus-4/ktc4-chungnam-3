package com.ktc.chungnam3.remembrall.recall.trace;

import com.ktc.chungnam3.remembrall.consent.config.ConsentConfiguration;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import com.ktc.chungnam3.remembrall.notification.config.NotificationProperties;
import com.ktc.chungnam3.remembrall.notification.push.PushSendException;
import com.ktc.chungnam3.remembrall.notification.push.PushSender;
import com.ktc.chungnam3.remembrall.notification.service.NotificationDeliveryService;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallPrompt;
import com.ktc.chungnam3.remembrall.recall.config.RecallAgentExecutionProperties;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolService;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventType;
import com.ktc.chungnam3.remembrall.recall.service.LocationEventService;
import com.ktc.chungnam3.remembrall.recall.service.ProposalValidator;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import com.ktc.chungnam3.remembrall.recall.service.RecallExecutionService;
import com.ktc.chungnam3.remembrall.recall.service.WalkingTimeCalculator;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.NotificationRepository;
import com.ktc.chungnam3.remembrall.repository.PublicCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = RecallTraceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key"
})
class RecallTraceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private MemberConsentService consents;
    @Autowired private ContentPersistenceService persistence;
    @Autowired private LocationEventService events;
    @Autowired private RecallExecutionRepository executions;
    @Autowired private NotificationRepository notifications;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ChatModel model;
    @Autowired private RecallCandidateRepository candidates;
    @MockitoSpyBean private RecallAgent agent;
    @Autowired private PushSender sender;
    @MockitoSpyBean private RecallTraceService traces;
    @MockitoSpyBean private RecallAgentInputService inputs;
    private UUID memberId;
    private UUID placeId;
    private UUID contentId;

    @BeforeEach
    void setUp() throws Exception {
        reset(model, sender, agent);
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        memberId = members.saveAndFlush(Member.create(AuthProvider.GUEST, UUID.randomUUID().toString(), NOW)).getId();
        var device = Device.create(memberId, "a".repeat(64), NOW.plusSeconds(86400), NOW);
        device.updateFcmToken("test-fcm-token", NOW);
        devices.saveAndFlush(device);
        consents.change(memberId, ConsentType.LOCATION_BASED_SERVICE, new ConsentRequest(true, "v1"));
        var claim = persistence.saveAndClaim(memberId, "trace-test-video");
        contentId = claim.contentId();
        persistence.applyOutcome(contentId, new AnalysisOutcome(ContentAnalysisStatus.SUCCESS, null,
                ContentSourceStatus.AVAILABLE, "둔산 카페", "카페 브런치", "CAFE", "trace-test", NOW,
                List.of(new AnalysisOutcome.PlaceResult("브런치를 소개한다", "둔산 카페", "대전 서구 둔산동",
                        36.351, 127.38, GeocodingProvider.LOCATIONIQ, "trace-cafe", "trace-cafe"))));
        placeId = jdbc.queryForObject("SELECT place_id FROM content_place WHERE content_id = ?", UUID.class, contentId);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(response(proposal(placeId)));
        when(sender.send(any())).thenReturn("projects/test/messages/123");
    }

    @Test
    void proposeCollectsAllStagesInOrderAndFlushesOnlyAfterSending() throws Exception {
        when(sender.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM recall_execution_trace", Integer.class)).isZero();
            return "projects/test/messages/123";
        });
        UUID id = accept();
        var trace = rows(id);
        assertThat(trace).extracting(Row::step).containsExactly("CANDIDATE_PREPARATION", "MODEL_CALL", "VALIDATION",
                "NOTIFICATION_CREATE", "NOTIFICATION_SEND");
        assertThat(trace).extracting(Row::seq).containsExactly(1, 2, 3, 4, 5);
        assertThat(trace).allSatisfy(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.startedAt()).isEqualTo(NOW);
            assertThat(row.durationMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(trace.getFirst().detail().get("originCount").asInt()).isEqualTo(1);
        assertThat(trace.getFirst().detail().get("originContentCount").asInt()).isEqualTo(1);
        assertThat(trace.get(1).name()).isEqualTo("trace-model");
        assertThat(trace.get(1).inputTokens()).isEqualTo(123);
        assertThat(trace.get(1).outputTokens()).isEqualTo(45);
        assertThat(trace.get(1).detail().has("agentInput")).isFalse();
        var notification = notifications.findByExecutionId(id).orElseThrow();
        assertThat(trace.get(3).detail().get("notificationId").asText()).isEqualTo(notification.getId().toString());
        assertThat(trace.get(3).detail().get("status").asText()).isEqualTo("PENDING");
        assertThat(trace.getLast().detail().get("status").asText()).isEqualTo("SENT");
        assertThat(trace.getLast().detail().get("failureCode").isNull()).isTrue();
        assertThat(executions.findById(id).orElseThrow().getResult()).isEqualTo(RecallExecutionResult.PROPOSE);
        verify(model, times(1)).call(any(Prompt.class));
        verify(traces, times(1)).save(any());
    }

    @Test
    void noActionHasNoNotificationStages() {
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":"쉬는 시간","title":null,"body":null}
                """));
        UUID id = accept();
        assertThat(rows(id)).extracting(Row::step).containsExactly("CANDIDATE_PREPARATION", "MODEL_CALL", "VALIDATION");
        assertThat(rows(id)).allSatisfy(row -> assertThat(row.success()).isTrue());
        assertThat(executions.findById(id).orElseThrow().getResult()).isEqualTo(RecallExecutionResult.NO_ACTION);
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void locationEventUsesTheSameToolLoopAndPersistsNoActionSummary(boolean reachLimit) throws Exception {
        var service = mock(RecallToolService.class);
        when(service.checkVisitConditions(any(), any())).thenReturn(new RecallToolResults.VisitConditionsResult(List.of(), null, List.of(), null));
        var binding = new RecallToolBinding(service, candidates);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        var toolCall = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("details", "function", "checkVisitConditions",
                        "{\"placeIds\":[\"" + contentId + "\"]}"))).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(123, 45)).build());
        when(model.call(any(Prompt.class))).thenReturn(toolCall, response("""
                {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":"server no-action","title":null,"body":null}
                """));
        doAnswer(call -> {
            try (var llm = new LlmRecallAgent(ChatClient.builder(model).build(), "fake", "trace-model",
                    new RecallPrompt("v2", "test"), () -> {}, new RecallAgentExecutionProperties(true,
                    reachLimit ? 1 : 6, java.time.Duration.ofSeconds(90), 6000), binding)) {
                return llm.executeWithStatistics(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            }
        }).when(agent).executeWithStatistics(any(), any(), any());

        UUID id = accept();

        var execution = executions.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.COMPLETED);
        assertThat(execution.getResult()).isEqualTo(RecallExecutionResult.NO_ACTION);
        assertThat(execution.getDecisionSummary()).contains(reachLimit ? "TOOL_CALL_LIMIT_REACHED" : "server no-action");
        assertThat(rows(id)).extracting(Row::step).containsExactly("CANDIDATE_PREPARATION", "MODEL_CALL", "TOOL_CALL",
                reachLimit ? "VALIDATION" : "MODEL_CALL", "VALIDATION");
        assertThat(notifications.count()).isZero();
        verify(service).checkVisitConditions(argThat(scope -> scope.memberId().equals(memberId)
                && scope.triggerPlaceId().equals(placeId)), eq(List.of(contentId)));
    }

    @Test
    void modelExceptionIsRecordedAndDoesNotLoseTheCandidateStage() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider exploded"));
        UUID id = accept();
        var trace = rows(id);
        assertThat(trace).extracting(Row::step).containsExactly("CANDIDATE_PREPARATION", "MODEL_CALL");
        assertThat(trace.getLast().success()).isFalse();
        assertThat(trace.getLast().errorMessage()).contains("provider exploded");
        assertThat(trace.getLast().inputTokens()).isNull();
        assertThat(executions.findById(id).orElseThrow().getFailureCode()).isEqualTo("AGENT_EXECUTION_ERROR");
    }

    @Test
    void responseConversionFailureKeepsReceivedUsageOnTheFailedModelStage() {
        when(model.call(any(Prompt.class))).thenReturn(response("not valid JSON"));
        UUID id = accept();
        var row = rows(id).getLast();
        assertThat(row.step()).isEqualTo("MODEL_CALL");
        assertThat(row.success()).isFalse();
        assertThat(row.errorMessage()).isNotBlank();
        assertThat(row.inputTokens()).isEqualTo(123);
        assertThat(row.outputTokens()).isEqualTo(45);
    }

    @Test
    void validationRejectionIsASeparateFailedStage() {
        when(model.call(any(Prompt.class))).thenReturn(response(proposal(UUID.randomUUID())));
        UUID id = accept();
        var trace = rows(id);
        assertThat(trace).extracting(Row::step).containsExactly("CANDIDATE_PREPARATION", "MODEL_CALL", "VALIDATION");
        assertThat(trace.get(1).success()).isTrue();
        assertThat(trace.getLast().success()).isFalse();
        assertThat(trace.getLast().detail().get("passed").asBoolean()).isFalse();
        assertThat(executions.findById(id).orElseThrow().getFailureCode()).isEqualTo("INVALID_PROPOSAL");
        assertThat(notifications.count()).isZero();
    }

    @Test
    void pushFailureKeepsCompletedExecutionAndRecordsTheFinalNotificationFailure() throws Exception {
        when(sender.send(any())).thenThrow(new PushSendException("UNREGISTERED"));
        UUID id = accept();
        var row = rows(id).getLast();
        assertThat(row.step()).isEqualTo("NOTIFICATION_SEND");
        assertThat(row.success()).isFalse();
        assertThat(row.detail().get("status").asText()).isEqualTo("FAILED");
        assertThat(row.detail().get("failureCode").asText()).isEqualTo("UNREGISTERED");
        assertThat(executions.findById(id).orElseThrow().getStatus()).isEqualTo(RecallExecutionStatus.COMPLETED);
    }

    @Test
    void missingTokenIsRecordedAsAFailedSendRatherThanASuccessfulMethodReturn() {
        jdbc.update("UPDATE device SET fcm_token = NULL WHERE member_id = ?", memberId);
        UUID id = accept();
        assertThat(rows(id).getLast().success()).isFalse();
        assertThat(rows(id).getLast().detail().get("failureCode").asText()).isEqualTo("NO_FCM_TOKEN");
        verifyNoInteractions(sender);
    }

    @Test
    void traceWriteFailureRollsBackTheWholeBatchButDoesNotChangeExecutionOrSentNotification() throws Exception {
        jdbc.execute("ALTER TABLE recall_execution_trace ADD CONSTRAINT trace_test_reject CHECK (false) NOT VALID");
        try {
            UUID id = accept();
            assertThat(rows(id)).isEmpty();
            var execution = executions.findById(id).orElseThrow();
            assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.COMPLETED);
            assertThat(execution.getResult()).isEqualTo(RecallExecutionResult.PROPOSE);
            assertThat(notifications.findByExecutionId(id).orElseThrow().getStatus().name()).isEqualTo("SENT");
            verify(sender, times(1)).send(any());
        } finally {
            jdbc.execute("ALTER TABLE recall_execution_trace DROP CONSTRAINT trace_test_reject");
        }
    }

    @Test
    void deletingAnExecutionCascadesToItsTrace() {
        UUID id = accept();
        assertThat(rows(id)).hasSize(5);
        executions.deleteById(id);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void preparationExceptionIsRecordedBeforeAnyModelCall() {
        doThrow(new IllegalStateException("candidate preparation failed")).when(inputs).prepare(any());
        UUID id = accept();
        assertThat(rows(id)).hasSize(1);
        assertThat(rows(id).getFirst().step()).isEqualTo("CANDIDATE_PREPARATION");
        assertThat(rows(id).getFirst().success()).isFalse();
        assertThat(rows(id).getFirst().errorMessage()).contains("candidate preparation failed");
        verify(model, times(0)).call(any(Prompt.class));
    }

    @Test
    void traceCommitIsIndependentOfTheCallersTransaction() {
        UUID triggerId = jdbc.queryForObject("SELECT id FROM trigger WHERE member_id = ? AND place_id = ?",
                UUID.class, memberId, placeId);
        UUID id = UUID.randomUUID();
        var transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> executions.insertPendingIfAbsent(id, triggerId,
                UUID.randomUUID(), "trace-test", NOW, NOW));
        var trace = traces.start(id);
        trace.validation(trace.begin(RecallTraceCollector.Step.VALIDATION, "proposal"), true, null);
        transactions.executeWithoutResult(status -> {
            traces.save(trace);
            status.setRollbackOnly();
        });
        assertThat(rows(id)).hasSize(1);
    }

    @Test
    void persistedDetailsAndErrorMessagesRemainBoundedAndKeepTheTruncationMarker() {
        UUID triggerId = jdbc.queryForObject("SELECT id FROM trigger WHERE member_id = ? AND place_id = ?",
                UUID.class, memberId, placeId);
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> executions.insertPendingIfAbsent(
                id, triggerId, UUID.randomUUID(), "trace-test", NOW, NOW));
        var limited = new RecallTraceService(jdbc, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC), mapper,
                new RecallTraceProperties(256, 32, true));
        var trace = limited.start(id);
        trace.recordToolCall("future-tool", NOW, 1, false, new IllegalStateException("오류😀".repeat(100)),
                "인자😀".repeat(200), "응답😀".repeat(200));
        limited.save(trace);
        var row = rows(id).getFirst();
        assertThat(row.detail().get("truncated").asBoolean()).isTrue();
        assertThat(mapper.writeValueAsBytes(row.detail())).hasSizeLessThanOrEqualTo(256);
        assertThat(row.errorMessage().codePointCount(0, row.errorMessage().length())).isEqualTo(32);
        assertThat(row.errorMessage()).endsWith("…");
    }

    @Test
    void databaseEnforcesUniqueSequenceAndAllowedSteps() {
        UUID id = accept();
        String insert = """
                INSERT INTO recall_execution_trace
                    (id, execution_id, seq, step, started_at, duration_ms, success, detail)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 0, true, '{}'::jsonb)
                """;
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), id, 1, "MODEL_CALL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), id, 6, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID accept() {
        UUID eventId = UUID.randomUUID();
        events.accept(memberId, new LocationEventRequest(placeId, eventId, LocationEventType.ENTER, NOW, null));
        return jdbc.queryForObject("SELECT id FROM recall_execution WHERE trigger_event_id = ?", UUID.class, eventId);
    }

    private List<Row> rows(UUID id) {
        return jdbc.query("SELECT * FROM recall_execution_trace WHERE execution_id = ? ORDER BY seq",
                (rs, index) -> new Row(rs.getString("step"), rs.getInt("seq"), rs.getString("name"),
                        rs.getTimestamp("started_at").toInstant(), rs.getDouble("duration_ms"), rs.getBoolean("success"),
                        rs.getString("error_message"), rs.getObject("input_tokens", Integer.class),
                        rs.getObject("output_tokens", Integer.class), mapper.readTree(rs.getString("detail"))), id);
    }

    private ChatResponse response(String raw) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(raw))),
                ChatResponseMetadata.builder().model("trace-model").usage(new DefaultUsage(123, 45)).build());
    }

    private String proposal(UUID proposedPlace) {
        return """
                {"result":"PROPOSE","proposalType":"PLACE","items":[{"contentId":"%s","placeId":"%s","source":"PERSONAL"}],
                 "decisionSummary":"저장한 장소 근처","title":"저장한 카페","body":"근처 카페에 들러 보세요."}
                """.formatted(contentId, proposedPlace);
    }

    private record Row(String step, int seq, String name, Instant startedAt, double durationMs, boolean success,
                       String errorMessage, Integer inputTokens, Integer outputTokens, JsonNode detail) {}

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @EntityScan("com.ktc.chungnam3.remembrall.domain")
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
    @EnableConfigurationProperties({RecallProperties.class, RecallCandidateProperties.class,
            RecallToolProperties.class, NotificationProperties.class})
    @Import({LocationEventService.class, RecallExecutionService.class, RecallAgentInputService.class,
            ProposalValidator.class, WalkingTimeCalculator.class, RecallCandidateRepository.class,
            PublicCandidateRepository.class, ContentPersistenceService.class, MemberConsentService.class,
            ConsentConfiguration.class, NotificationDeliveryService.class, RecallTraceConfiguration.class})
    static class TestApplication {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ChatModel model() {
            var model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(ChatOptions.builder().build());
            return model;
        }

        @Bean
        RecallAgent agent(ChatModel model) {
            return new LlmRecallAgent(ChatClient.builder(model).build(), "fake", "trace-model",
                    new RecallPrompt("v1", "trace test"), () -> {});
        }

        @Bean
        PushSender sender() {
            return mock(PushSender.class);
        }
    }
}
