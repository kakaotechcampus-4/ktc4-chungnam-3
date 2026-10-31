package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalPayload;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecallEvaluationRunnerTest {
    @TempDir Path directory;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RecallEvaluationRunner runner = new RecallEvaluationRunner();
    private final RecallEvaluationChecker checker = new RecallEvaluationChecker();

    @Test
    void fakeAgentWritesMetadataAndCreatesSeparateFilesWithoutChangingPreviousResults() throws Exception {
        var scene = scenes()[0];
        var restaurant = scene.input().nearbyPersonalCandidates().getFirst();
        var result = proposal(ProposalType.COURSE, originItem(scene), new ProposalPayload.Item(
                restaurant.contents().getFirst().contentId(), restaurant.placeId(), ProposalSource.PERSONAL));
        String raw = "fake raw model response";
        var calls = new AtomicInteger();
        var agent = fakeAgent(input -> {
            assertThat(input).isEqualTo(scene.input());
            calls.incrementAndGet();
            return new LlmRecallAgent.Invocation(raw, result, 12.5, 200, 20);
        });
        Path output = directory.resolve("results.jsonl");
        Files.writeString(output, "previous results\n");
        var lines = runner.run(List.of(scene), 2, "FAKE", "fake-model", agent, output);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.checks().expectationMatched()).isTrue();
            assertThat(line.checks().formatPassed()).isTrue();
            assertThat(line.checks().errors()).isEmpty();
        });
        Path firstOutput = outputFor(lines.getFirst().runId());
        var jsonLines = Files.readAllLines(firstOutput);
        assertThat(jsonLines).hasSize(2);
        var json = mapper.readTree(jsonLines.getFirst());
        assertThat(json.get("runId").asText()).isEqualTo(lines.getFirst().runId().toString());
        assertThat(json.get("runStartedAt").asText()).isEqualTo(lines.getFirst().runStartedAt().toString());
        assertThat(lines).extracting(RecallEvaluationRunner.Line::runId).containsOnly(lines.getFirst().runId());
        assertThat(lines).extracting(RecallEvaluationRunner.Line::runStartedAt).containsOnly(lines.getFirst().runStartedAt());
        assertThat(json.get("sceneId").asText()).isEqualTo(scene.id());
        assertThat(json.get("repetition").asInt()).isEqualTo(1);
        assertThat(json.get("model").asText()).isEqualTo("fake-model");
        assertThat(json.get("agentVersion").asText()).isEqualTo(agent.version());
        assertThat(json.get("rawResponse").asText()).isEqualTo(raw);
        assertThat(json.at("/result/result").asText()).isEqualTo("PROPOSE");
        assertThat(json.at("/checks/expectationMatched").asBoolean()).isTrue();
        assertThat(json.get("responseTimeMillis").asDouble()).isEqualTo(12.5);
        assertThat(json.get("inputTokens").asInt()).isEqualTo(200);
        assertThat(json.get("outputTokens").asInt()).isEqualTo(20);
        var second = runner.run(List.of(scene), 1, "FAKE", "fake-model", agent, output);
        assertThat(second.getFirst().runId()).isNotEqualTo(lines.getFirst().runId());
        assertThat(second.getFirst().agentVersion()).isEqualTo(lines.getFirst().agentVersion());
        Path secondOutput = outputFor(second.getFirst().runId());
        assertThat(secondOutput).isNotEqualTo(firstOutput);
        assertThat(secondOutput.getParent()).isNotEqualTo(firstOutput.getParent());
        assertThat(firstOutput.getFileName().toString()).isEqualTo("results.jsonl");
        assertThat(secondOutput.getFileName().toString()).isEqualTo("results.jsonl");
        assertThat(Files.readAllLines(firstOutput)).containsExactlyElementsOf(jsonLines);
        assertThat(Files.readAllLines(secondOutput)).hasSize(1);
        assertThat(Files.readString(output)).isEqualTo("previous results\n");
        var console = new ByteArrayOutputStream();
        runner.printSummary(lines, new PrintStream(console, true, StandardCharsets.UTF_8));
        assertThat(console.toString(StandardCharsets.UTF_8))
                .contains("FAKE/fake-model", "expected=100.0%", "format=100.0%", "avgLatency=12.5ms",
                        "avgInputTokens=200.0", "avgOutputTokens=20.0");
    }

    @Test
    void modelAndParsingFailuresAreRecordedAndDoNotStopFollowingRuns() throws Exception {
        var scene = scenes()[1];
        var calls = new AtomicInteger();
        var agent = fakeAgent(input -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new LlmRecallAgent.CallException(new IllegalArgumentException("bad JSON"),
                        new LlmRecallAgent.Invocation("broken raw", null, 20, 50, 10));
            }
            if (call == 2) {
                throw new IllegalStateException("unavailable");
            }
            return new LlmRecallAgent.Invocation("ok", RecallAgentResult.noAction(), 10, 40, 5);
        });
        var output = directory.resolve("failures.jsonl");
        var lines = runner.run(List.of(scene), 3, "FAKE", "fake-model", agent, output);
        assertThat(lines).hasSize(3);
        assertThat(lines).allSatisfy(line -> assertThat(line.agentVersion()).isEqualTo(agent.version()));
        assertThat(Files.readAllLines(outputFor(lines.getFirst().runId()))).allSatisfy(line ->
                assertThat(mapper.readTree(line).get("agentVersion").asText()).isEqualTo(agent.version()));
        assertThat(lines.getFirst().rawResponse()).isEqualTo("broken raw");
        assertThat(lines.getFirst().checks().formatPassed()).isFalse();
        assertThat(lines.get(1).inputTokens()).isNull();
        assertThat(lines.getLast().checks().expectationMatched()).isTrue();
    }

    @Test
    void jsonLineUsesVersionProvidedByActualLlmAgent() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":null,"title":null,"body":null}
                """)))));
        var prompt = RecallPrompt.load(new ClassPathResource("recall/prompts/decision.yaml"));
        try (var agent = new LlmRecallAgent(ChatClient.builder(model).build(), "google-genai", "test-model",
                prompt, () -> {})) {
            Path output = directory.resolve("version.jsonl");
            var lines = runner.run(List.of(scenes()[1]), 1, "GOOGLE_GENAI", "test-model",
                    RecallEvaluationRunner.EvaluationAgent.from(agent), output);
            assertThat(lines.getFirst().agentVersion()).isEqualTo(agent.version());
            assertThat(mapper.readTree(Files.readString(outputFor(lines.getFirst().runId()))).get("agentVersion").asText())
                    .isEqualTo(agent.version());
        }
    }

    @Test
    void multipleModelsInOneExecutionShareOneFileAndOneRunId() throws Exception {
        var scene = scenes()[1];
        var agent = fakeAgent(input -> new LlmRecallAgent.Invocation("ok", RecallAgentResult.noAction(), 1, 10, 5));
        Path path;
        UUID runId;
        try (var run = EvaluationRun.start(directory.resolve("models.jsonl"))) {
            path = run.output();
            runId = run.runId();
            var first = runner.run(List.of(scene), 1, "FAKE", "first-model", agent, run);
            var second = runner.run(List.of(scene), 1, "FAKE", "second-model", agent, run);
            assertThat(first.getFirst().runId()).isEqualTo(runId);
            assertThat(second.getFirst().runId()).isEqualTo(runId);
            assertThat(second.getFirst().runStartedAt()).isEqualTo(first.getFirst().runStartedAt());
        }
        var rows = Files.readAllLines(path).stream().map(mapper::readTree).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("model").asText()).containsExactly("first-model", "second-model");
        assertThat(rows).extracting(row -> row.get("runId").asText()).containsOnly(runId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknownContent", "unknownPlace", "source", "duplicatePlace", "courseOne",
            "placeTwo", "contentWithPlace", "noActionWithItems"})
    void checkerRejectsUnknownIdsIncorrectSourcesAndInvalidShapes(String invalid) throws Exception {
        var scene = scenes()[0];
        var item = originItem(scene);
        RecallAgentResult result = switch (invalid) {
            case "unknownContent" -> proposal(ProposalType.PLACE,
                    new ProposalPayload.Item(UUID.randomUUID(), item.placeId(), item.source()));
            case "unknownPlace" -> proposal(ProposalType.PLACE,
                    new ProposalPayload.Item(item.contentId(), UUID.randomUUID(), item.source()));
            case "source" -> proposal(ProposalType.PLACE,
                    new ProposalPayload.Item(item.contentId(), item.placeId(), ProposalSource.PUBLIC));
            case "duplicatePlace" -> proposal(ProposalType.COURSE, item, item);
            case "courseOne" -> proposal(ProposalType.COURSE, item);
            case "placeTwo" -> proposal(ProposalType.PLACE, item, item);
            case "contentWithPlace" -> proposal(ProposalType.CONTENT, item);
            default -> new RecallAgentResult(RecallExecutionResult.NO_ACTION, null,
                    new ProposalPayload(List.of(item)), null, null, null);
        };
        var checks = checker.check(scene, result);
        assertThat(checks.formatPassed()).isFalse();
        assertThat(checks.expectationMatched()).isFalse();
        assertThat(checks.errors()).isNotEmpty();
    }

    @Test
    void expectationsCheckRequiredForbiddenAndPublicItemsSeparatelyFromFormat() throws Exception {
        var scenes = scenes();
        var personalPriority = scenes[5];
        var missingRequired = checker.check(personalPriority, proposal(ProposalType.PLACE, originItem(personalPriority)));
        assertThat(missingRequired.formatPassed()).isTrue();
        assertThat(missingRequired.errors()).contains("MISSING_REQUIRED_CONTENT");
        var publicPlace = personalPriority.input().nearbyPublicCandidates().getFirst();
        var publicItem = new ProposalPayload.Item(publicPlace.contents().getFirst().contentId(),
                publicPlace.placeId(), ProposalSource.PUBLIC);
        var forbiddenPublic = checker.check(personalPriority, proposal(ProposalType.PLACE, publicItem));
        assertThat(forbiddenPublic.formatPassed()).isTrue();
        assertThat(forbiddenPublic.errors()).contains("PUBLIC_NOT_ALLOWED", "FORBIDDEN_CONTENT", "FORBIDDEN_PLACE");
        assertThat(checker.check(scenes[4], proposal(ProposalType.PLACE, publicItem)).expectationMatched()).isTrue();
        var injection = scenes[6];
        var bait = injection.input().nearbyPersonalCandidates().getLast();
        var rejected = checker.check(injection, proposal(ProposalType.PLACE,
                new ProposalPayload.Item(bait.contents().getFirst().contentId(), bait.placeId(), ProposalSource.PERSONAL)));
        assertThat(rejected.formatPassed()).isTrue();
        assertThat(rejected.errors()).contains("FORBIDDEN_CONTENT", "FORBIDDEN_PLACE");
    }

    @Test
    void sceneSetHasAtLeastTenUniqueScenesAndEveryExpectedIdIsInInput() throws Exception {
        var scenes = List.of(scenes());
        assertThat(scenes).hasSizeGreaterThanOrEqualTo(10);
        assertThat(scenes).extracting(EvaluationScene::id).doesNotHaveDuplicates();
        for (var scene : scenes) {
            var json = mapper.writeValueAsString(scene.input());
            var expected = scene.expectation();
            java.util.stream.Stream.of(expected.requiredContentIds(), expected.requiredPlaceIds(),
                            expected.forbiddenContentIds(), expected.forbiddenPlaceIds())
                    .flatMap(List::stream).forEach(id -> assertThat(json).contains(id.toString()));
            assertThat(scene.input().situation().localDateTime().getDayOfWeek())
                    .isEqualTo(scene.input().situation().dayOfWeek());
        }
    }

    private EvaluationScene[] scenes() throws Exception {
        try (var stream = new ClassPathResource("recall/evaluation/scenes.json").getInputStream()) {
            return mapper.readValue(stream, EvaluationScene[].class);
        }
    }

    private Path outputFor(UUID runId) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> !file.getParent().equals(directory) && file.toString().endsWith(".jsonl")).toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    String first = reader.readLine();
                    if (first != null && runId.toString().equals(mapper.readTree(first).path("runId").asText())) {
                        return path;
                    }
                }
            }
        }
        throw new IllegalStateException("Evaluation output not found for " + runId);
    }

    private RecallEvaluationRunner.EvaluationAgent fakeAgent(InvocationProvider invocation) {
        return new RecallEvaluationRunner.EvaluationAgent() {
            @Override
            public String version() {
                return "llm|fake|fake-model|prompt-v1-a3f9c2";
            }

            @Override
            public LlmRecallAgent.Invocation invoke(RecallAgentInput input) throws Exception {
                return invocation.invoke(input);
            }
        };
    }

    @FunctionalInterface
    private interface InvocationProvider {
        LlmRecallAgent.Invocation invoke(RecallAgentInput input) throws Exception;
    }

    private ProposalPayload.Item originItem(EvaluationScene scene) {
        return new ProposalPayload.Item(scene.input().origin().contents().getFirst().contentId(),
                scene.input().origin().placeId(), ProposalSource.PERSONAL);
    }

    private RecallAgentResult proposal(ProposalType type, ProposalPayload.Item... items) {
        return RecallAgentResult.propose(type, new ProposalPayload(List.of(items)), "Decision", "Title", "Body");
    }
}
