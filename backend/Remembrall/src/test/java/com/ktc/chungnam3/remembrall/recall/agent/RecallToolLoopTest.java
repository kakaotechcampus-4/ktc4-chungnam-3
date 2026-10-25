package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.config.RecallAgentExecutionProperties;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolScope;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolService;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecallToolLoopTest {
    private static final String NO_ACTION = "{\"result\":\"NO_ACTION\",\"proposalType\":null,\"items\":[],\"decisionSummary\":\"판단 완료\",\"title\":null,\"body\":null}";
    private final ChatModel model = mock(ChatModel.class);
    private final RecallToolService service = mock(RecallToolService.class);
    private final UUID executionId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();
    private final RecallToolScope scope = new RecallToolScope(UUID.randomUUID(), UUID.randomUUID(), 36.35, 127.38);
    private final RecallToolBinding binding = spy(new RecallToolBinding(service, null));
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RecallTraceCollector trace = new RecallTraceCollector(executionId, Clock.systemUTC(), mapper,
            new RecallTraceProperties(16384, 1000, false));

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        doReturn(scope).when(binding).scope(executionId);
        when(service.checkVisitConditions(any(), any())).thenReturn(new RecallToolResults.VisitConditionsResult(List.of(), null, List.of(), null));
    }

    @Test
    void springLoopExecutesBothToolsAndFeedsExactResponsesBackInOrder() throws Exception {
        when(service.searchCandidates(any(), any(), any(), any(), anySet(), anySet()))
                .thenReturn(new RecallToolResults.SearchResult(List.of(), List.of(), null));
        var count = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            Prompt prompt = call.getArgument(0);
            int round = count.incrementAndGet();
            var options = (ToolCallingChatOptions) prompt.getOptions();
            assertThat(options.getToolCallbacks()).hasSize(2).allSatisfy(tool ->
                    assertThat(tool.getToolDefinition().inputSchema()).doesNotContain("memberId", "triggerPlaceId"));
            if (round > 1) {
                var messages = prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                        .map(ToolResponseMessage.class::cast).toList();
                var actual = messages.getLast().getResponses().getFirst().responseData();
                var recorded = trace.entries().stream().filter(entry -> entry.step() == RecallTraceCollector.Step.TOOL_CALL).toList().getLast();
                assertThat(recorded.detail().path("response").asText()).isEqualTo(actual);
            }
            return switch (round) {
                case 1 -> tool("searchCandidates", "{\"condition\":\"따뜻한 점심\"}");
                case 2 -> tool("checkVisitConditions", "{\"placeIds\":[\"" + contentId + "\"]}");
                default -> response(NO_ACTION);
            };
        });
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            var result = agent.invoke(executionId, input(), trace);
            assertThat(result.result().result()).isEqualTo(RecallExecutionResult.NO_ACTION);
            assertThat(result.inputTokens()).isEqualTo(30);
            assertThat(result.outputTokens()).isEqualTo(6);
            assertThat(trace.entries()).extracting(RecallTraceCollector.Entry::step).containsExactly(
                    RecallTraceCollector.Step.MODEL_CALL, RecallTraceCollector.Step.TOOL_CALL,
                    RecallTraceCollector.Step.MODEL_CALL, RecallTraceCollector.Step.TOOL_CALL, RecallTraceCollector.Step.MODEL_CALL);
            assertThat(trace.entries().stream().filter(entry -> entry.step() == RecallTraceCollector.Step.MODEL_CALL))
                    .allSatisfy(entry -> {
                        assertThat(entry.inputTokens()).isEqualTo(10);
                        assertThat(entry.outputTokens()).isEqualTo(2);
                    });
        }
    }

    @Test
    void searchesAutomaticallyExcludeInitialInputAndOnlyResultsActuallyReturnedToTheModel() throws Exception {
        UUID discoveredPlace = UUID.randomUUID();
        UUID discoveredContent = UUID.randomUUID();
        var count = new AtomicInteger();
        when(service.searchCandidates(any(), any(), any(), any(), anySet(), anySet())).thenAnswer(call -> {
            assertThat((java.util.Set<UUID>) call.getArgument(5)).contains(scope.triggerPlaceId());
            if (count.incrementAndGet() == 2) {
                assertThat((java.util.Set<UUID>) call.getArgument(4)).contains(discoveredContent);
                assertThat((java.util.Set<UUID>) call.getArgument(5)).contains(discoveredPlace);
            }
            return new RecallToolResults.SearchResult(List.of(new RecallToolResults.PlaceCandidate(discoveredPlace, "대안", 10,
                    List.of(new RecallToolResults.ContentCandidate(discoveredContent, ProposalSource.PUBLIC, "콘텐츠", "FOOD", "요약", null)))), List.of(), null);
        });
        when(model.call(any(Prompt.class))).thenReturn(tool("searchCandidates", "{\"condition\":\"실내\",\"source\":\"PUBLIC\"}"),
                tool("searchCandidates", "{\"condition\":\"다른 식당\",\"source\":\"PERSONAL\"}"), response(NO_ACTION));
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            agent.invoke(executionId, input(), trace);
        }
        assertThat(count).hasValue(2);
        verify(service).searchCandidates(eq(scope), eq("실내"), isNull(), eq(RecallToolResults.SearchSource.PUBLIC), anySet(), anySet());
        verify(service).searchCandidates(eq(scope), eq("다른 식당"), isNull(), eq(RecallToolResults.SearchSource.PERSONAL), anySet(), anySet());
    }

    @Test
    void visitSummaryIsNotClippedAgainAtSixHundredCharacters() throws Exception {
        String summary = "설명".repeat(400) + " 마지막에 오늘 휴무";
        var condition = new RecallToolResults.OperatingCondition(contentId, ProposalSource.PERSONAL, "제목", summary,
                "소개", java.time.Instant.EPOCH, "PERSONAL_SAVE");
        var place = new RecallToolResults.PlaceConditions(scope.triggerPlaceId(), "장소", List.of(condition), null);
        when(service.checkVisitConditions(any(), any())).thenReturn(
                new RecallToolResults.VisitConditionsResult(List.of(place), null, List.of(), null));
        var round = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            if (round.incrementAndGet() == 1) return tool("checkVisitConditions", "{\"placeIds\":[\"" + scope.triggerPlaceId() + "\"]}");
            Prompt prompt = call.getArgument(0);
            var data = prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).findFirst().orElseThrow().getResponses().getFirst().responseData();
            assertThat(mapper.readTree(data).path("places").get(0).path("operatingConditions").get(0).path("summary").asText())
                    .isEqualTo(summary);
            return response(NO_ACTION);
        });
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 24000, System::nanoTime)) {
            agent.invoke(executionId, input(), trace);
        }
    }

    @Test
    void modelSuppliedMemberAndOriginCannotOverrideServerToolContext() throws Exception {
        UUID attacker = UUID.randomUUID();
        when(model.call(any(Prompt.class))).thenReturn(tool("checkVisitConditions",
                "{\"placeIds\":[\"" + contentId + "\"],\"memberId\":\"" + attacker + "\",\"triggerPlaceId\":\"" + attacker + "\"}"), response(NO_ACTION));
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            agent.invoke(executionId, input(), trace);
        }
        verify(service).checkVisitConditions(scope, List.of(contentId));
        verify(service, never()).checkVisitConditions(argThat(value -> value.memberId().equals(attacker)), any());
    }

    @Test
    void toolSwitchIsPartOfTheVersionAndArgumentSchemasHaveDescriptions() throws Exception {
        assertThat(binding.callbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("checkVisitConditions", "searchCandidates");
        try (var enabled = agent(true, 6, Duration.ofSeconds(90), 6000, System::nanoTime);
             var disabled = agent(false, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            assertThat(enabled.version()).endsWith("|tools-on").isNotEqualTo(disabled.version());
            assertThat(disabled.version()).endsWith("|tools-off");
        }
        for (var callback : binding.callbacks()) {
            var schema = mapper.readTree(callback.getToolDefinition().inputSchema());
            assertThat(schema.path("properties").properties()).allSatisfy(entry ->
                    assertThat(entry.getValue().path("description").asText()).isNotBlank());
            assertThat(callback.getToolDefinition().description()).isNotBlank();
        }
    }

    @Test
    void reachingToolLimitReturnsNoActionAndRecordsReasonWithoutCallingModelAgain() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(tool("checkVisitConditions", "{\"placeIds\":[]}"));
        try (var agent = agent(true, 1, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            var result = agent.invoke(executionId, input(), trace);
            assertThat(result.result().result()).isEqualTo(RecallExecutionResult.NO_ACTION);
            assertThat(result.result().decisionSummary()).contains("TOOL_CALL_LIMIT_REACHED");
            assertThat(trace.entries()).anySatisfy(entry -> assertThat(entry.detail().path("reason").asText()).isEqualTo("TOOL_CALL_LIMIT_REACHED"));
        }
        verify(model, times(1)).call(any(Prompt.class));
        verify(service, times(1)).checkVisitConditions(scope, List.of());
    }

    @Test
    void batchedToolCallsCannotExecutePastTheRequestLimit() throws Exception {
        var calls = List.of(new AssistantMessage.ToolCall("1", "function", "checkVisitConditions", "{\"placeIds\":[]}"),
                new AssistantMessage.ToolCall("2", "function", "checkVisitConditions", "{\"placeIds\":[]}"));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("").toolCalls(calls).build()))));
        try (var agent = agent(true, 1, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            assertThat(agent.invoke(executionId, input(), trace).result().decisionSummary()).contains("TOOL_CALL_LIMIT_REACHED");
        }
        verify(service, times(1)).checkVisitConditions(scope, List.of());
    }

    @Test
    void configuredLimitIsNotOverriddenByTheFrameworksDefaultPerToolLimit() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(tool("checkVisitConditions", "{\"placeIds\":[]}"));
        try (var agent = agent(true, 45, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            assertThat(agent.invoke(executionId, input(), trace).result().decisionSummary()).contains("TOOL_CALL_LIMIT_REACHED");
        }
        verify(model, times(45)).call(any(Prompt.class));
        verify(service, times(45)).checkVisitConditions(scope, List.of());
    }

    @Test
    void totalDeadlineStopsExecutionWithoutSleepingAndKeepsTokens() throws Exception {
        var nanos = new AtomicLong();
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            nanos.set(Duration.ofSeconds(2).toNanos());
            return response(NO_ACTION);
        });
        try (var agent = agent(true, 6, Duration.ofSeconds(1), 6000, nanos::get)) {
            assertThatThrownBy(() -> agent.invoke(executionId, input(), trace))
                    .isInstanceOfSatisfying(LlmRecallAgent.CallException.class, exception -> {
                        assertThat(exception.getCause()).isInstanceOf(RecallAgentSession.ExecutionTimeout.class);
                        assertThat(exception.invocation().inputTokens()).isEqualTo(10);
                    });
        }
        assertThat(trace.entries()).anySatisfy(entry -> assertThat(entry.name()).isEqualTo("agent-execution-timeout"));
    }

    @Test
    void disabledToolsRegisterNothingAndDoNotResolveScopeOrCallServices() throws Exception {
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            Prompt prompt = call.getArgument(0);
            assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()).isNullOrEmpty();
            assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolContext()).isNullOrEmpty();
            return response(NO_ACTION);
        });
        try (var agent = agent(false, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            assertThat(agent.invoke(input()).result().decisionSummary()).isEqualTo("판단 완료");
            assertThat(agent.version()).endsWith("|tools-off");
        }
        verifyNoInteractions(service);
        verify(binding, never()).scope(any());
    }

    @Test
    void toolResponseIsBoundedValidJsonAndExactlyMatchesTrace() throws Exception {
        var huge = new RecallToolResults.OperatingCondition(contentId, ProposalSource.PERSONAL, "긴 콘텐츠", "요약😀".repeat(2000),
                "운영 설명", java.time.Instant.EPOCH, "PERSONAL_SAVE");
        var place = new RecallToolResults.PlaceConditions(scope.triggerPlaceId(), "장소", List.of(huge), null);
        when(service.checkVisitConditions(any(), any())).thenReturn(
                new RecallToolResults.VisitConditionsResult(List.of(place, place, place), null, List.of(), null));
        var count = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            if (count.incrementAndGet() == 1) {
                return tool("checkVisitConditions", "{\"placeIds\":[\"" + contentId + "\"]}");
            }
            Prompt prompt = call.getArgument(0);
            var result = prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).findFirst().orElseThrow().getResponses().getFirst().responseData();
            assertThat(result.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(512);
            assertThat(mapper.readTree(result).isObject()).isTrue();
            assertThat(trace.entries().getLast().detail().path("response").asText()).isEqualTo(result);
            return response(NO_ACTION);
        });
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 512, System::nanoTime)) {
            agent.invoke(executionId, input(), trace);
        }
    }

    @Test
    void serviceErrorRemainsAResultAndIsRecordedAsAnUnsuccessfulToolCall() throws Exception {
        when(service.checkVisitConditions(any(), any())).thenReturn(new RecallToolResults.VisitConditionsResult(List.of(), null, List.of(), "대상을 찾을 수 없습니다."));
        when(model.call(any(Prompt.class))).thenReturn(tool("checkVisitConditions", "{\"placeIds\":[]}"), response(NO_ACTION));
        try (var agent = agent(true, 6, Duration.ofSeconds(90), 6000, System::nanoTime)) {
            assertThat(agent.invoke(executionId, input(), trace).result().result()).isEqualTo(RecallExecutionResult.NO_ACTION);
        }
        assertThat(trace.entries().get(1).success()).isFalse();
        assertThat(trace.entries().get(1).detail().path("response").asText()).contains("대상을 찾을 수 없습니다.");
    }

    private LlmRecallAgent agent(boolean enabled, int maxCalls, Duration timeout, int responseBytes,
                                 java.util.function.LongSupplier nanos) throws Exception {
        return new LlmRecallAgent(ChatClient.builder(model).build(), "fake", "fake-model",
                RecallPrompt.load(new ClassPathResource("recall/prompts/decision.yaml")), () -> {},
                new RecallAgentExecutionProperties(enabled, maxCalls, timeout, responseBytes), binding, nanos);
    }

    private RecallAgentInput input() {
        return new RecallAgentInput(new RecallAgentInput.Origin(scope.triggerPlaceId(), "출발점", scope.latitude(), scope.longitude(), List.of()),
                List.of(), List.of(), List.of(), new RecallAgentInput.Situation(LocalDateTime.parse("2026-10-10T18:10:00"), DayOfWeek.SATURDAY));
    }

    private ChatResponse tool(String name, String args) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", name, args))).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 2)).build());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 2)).build());
    }
}
