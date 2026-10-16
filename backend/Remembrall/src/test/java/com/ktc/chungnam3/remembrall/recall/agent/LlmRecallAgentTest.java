package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.evaluation.EvaluationScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRecallAgentTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ChatModel model = mock(ChatModel.class);

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void convertsStructuredProposalAndSendsCompactInputWithSeoulTimesWithoutTools() throws Exception {
        var scene = scene();
        UUID contentId = scene.input().origin().contents().getFirst().contentId();
        UUID placeId = scene.input().origin().placeId();
        String raw = mapper.writeValueAsString(new RecallLlmResponse(RecallExecutionResult.PROPOSE, ProposalType.PLACE,
                List.of(new RecallLlmResponse.Item(contentId, placeId, ProposalSource.PERSONAL)), "저장한 카페를 다시 떠올릴 시간",
                "저장해 둔 카페가 가까워요", "예전에 기억해 둔 커피 한 잔을 떠올려 보세요."));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).hasSize(2);
            assertThat(prompt.getSystemMessage().getText()).startsWith(RecallPrompt.load(
                    new ClassPathResource("recall/prompts/decision.yaml")).text()).contains("JSON")
                    .doesNotContain("version: v1", "text:", "prompt-v1-");
            String input = prompt.getUserMessage().getText();
            assertThat(input).doesNotContain("\n");
            var json = mapper.readTree(input);
            assertThat(json.at("/origin/contents/0/savedAt").asText()).isEqualTo("2026-02-14T12:00+09:00");
            assertThat(json.at("/situation/localDateTime").asText()).isEqualTo("2026-10-10T18:10:00");
            assertThat(json.at("/situation/dayOfWeek").asText()).isEqualTo("SATURDAY");
            return response(raw);
        });
        try (var agent = agent()) {
            var invocation = agent.invoke(scene.input());
            assertThat(invocation.rawResponse()).isEqualTo(raw);
            assertThat(invocation.result()).isEqualTo(RecallAgentResult.propose(ProposalType.PLACE,
                    new ProposalPayload(List.of(new ProposalPayload.Item(contentId, placeId, ProposalSource.PERSONAL))),
                    "저장한 카페를 다시 떠올릴 시간", "저장해 둔 카페가 가까워요", "예전에 기억해 둔 커피 한 잔을 떠올려 보세요."));
            assertThat(invocation.inputTokens()).isEqualTo(123);
            assertThat(invocation.outputTokens()).isEqualTo(45);
            assertThat(invocation.responseTimeMillis()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void convertsNoActionAndKeepsDecisionSummary() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":"출근 시간","title":null,"body":null}
                """));
        try (var agent = agent()) {
            var result = agent.execute(UUID.randomUUID(), scene().input());
            assertThat(result.result()).isEqualTo(RecallExecutionResult.NO_ACTION);
            assertThat(result.proposalType()).isNull();
            assertThat(result.decisionSummary()).isEqualTo("출근 시간");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "null", "{}", "{\"result\":\"UNKNOWN\"}"})
    void parsingFailureThrowsAndPreservesRawResponse(String raw) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response(raw));
        try (var agent = agent()) {
            assertThatThrownBy(() -> agent.execute(UUID.randomUUID(), scene().input()))
                    .isInstanceOfSatisfying(LlmRecallAgent.CallException.class, exception -> {
                        assertThat(exception.invocation().rawResponse()).isEqualTo(raw);
                        assertThat(exception.invocation().result()).isNull();
                        assertThat(exception.invocation().inputTokens()).isEqualTo(123);
                    });
        }
    }

    @Test
    void absentUsageRemainsUnknownInsteadOfBeingRecordedAsZero() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":"쉬는 시간","title":null,"body":null}
                """)))));
        try (var agent = agent()) {
            var invocation = agent.invoke(scene().input());
            assertThat(invocation.inputTokens()).isNull();
            assertThat(invocation.outputTokens()).isNull();
        }
    }

    @Test
    void modelFailureThrowsWithoutInventingResponseOrUsage() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("test failure"));
        try (var agent = agent()) {
            assertThatThrownBy(() -> agent.execute(UUID.randomUUID(), scene().input()))
                    .isInstanceOfSatisfying(LlmRecallAgent.CallException.class, exception -> {
                        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                        assertThat(exception.invocation().rawResponse()).isNull();
                        assertThat(exception.invocation().inputTokens()).isNull();
                    });
        }
    }

    private LlmRecallAgent agent() throws Exception {
        var prompt = RecallPrompt.load(new ClassPathResource("recall/prompts/decision.yaml"));
        return new LlmRecallAgent(ChatClient.builder(model).build(), "google-genai", "gemini-3.6-flash", prompt, () -> {});
    }

    private EvaluationScene scene() throws Exception {
        try (var stream = new ClassPathResource("recall/evaluation/scenes.json").getInputStream()) {
            return mapper.readValue(stream, EvaluationScene[].class)[0];
        }
    }

    private ChatResponse response(String raw) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(raw))),
                ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(123, 45)).build());
    }
}
