package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalPayload;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.service.ProposalValidator;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = SeedEvaluationConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/not-the-evaluation-database"
})
class SeedEvaluationIntegrationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private SeedEvaluationRunner runner;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private SeedEvaluationSeeder seeder;
    @MockitoSpyBean private RecallAgentInputService inputs;
    @MockitoSpyBean private ProposalValidator validator;
    @MockitoBean private EmbeddingClient embeddings;
    @Autowired private com.ktc.chungnam3.remembrall.recall.tool.RecallToolService tools;
    @Autowired private com.ktc.chungnam3.remembrall.recall.weather.WeatherClient weather;
    @TempDir Path directory;

    @BeforeEach
    void setUp() {
        float[] vector = new float[768];
        vector[0] = 1;
        when(embeddings.embedDocument(anyString())).thenReturn(new EmbeddingClient.Result(vector, "fixed-vector"));
    }

    @Test
    void optionalSceneWeatherAndRecentNotificationsAreSeededAndResetWithoutRealWeatherCalls() throws Exception {
        var original = scenes().getFirst();
        var now = SeedEvaluationSeeder.seoulInstant(original.event().occurredAt());
        var sent = now.minusSeconds(3600);
        var configured = new SeedEvaluationScene(original.id(), original.split(), original.kind(), original.seed(), original.event(),
                original.reference(), original.judgeNotes(), original.goodChoices(),
                new SeedEvaluationScene.Weather(com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Precipitation.RAIN,
                        17.0, com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Sky.OVERCAST, List.of(80, 70)),
                List.of(new SeedEvaluationScene.RecentNotification(original.event().placeKey(),
                        sent.atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime().toString(), false)));
        configured = mapper.readValue(mapper.writeValueAsString(configured), SeedEvaluationScene.class);
        var seeded = seeder.resetAndSeed(configured);
        var definition = original.seed().places().get(original.event().placeKey());
        UUID placeId = seeded.placeIds().get(original.event().placeKey());
        var scope = new com.ktc.chungnam3.remembrall.recall.tool.RecallToolScope(seeded.memberId(), placeId,
                definition.latitude(), definition.longitude());
        var checked = tools.checkVisitConditions(scope, List.of(placeId));
        assertThat(weather).isInstanceOf(FakeWeatherClient.class);
        assertThat(checked.weather().current().precipitation())
                .isEqualTo(com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Precipitation.RAIN);
        assertThat(checked.weather().rainProbabilities()).extracting(
                com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.RainProbability::percent).containsExactly(80, 70);
        assertThat(checked.places().getFirst().recentProposals().notifications()).hasSize(1);
        assertThat(checked.places().getFirst().recentProposals().notifications().getFirst().sentAt()).isEqualTo(sent);
        assertThat(checked.places().getFirst().recentProposals().notifications().getFirst().opened()).isFalse();
        seeder.resetAndSeed(original);
        assertThat(weather.read(definition.latitude(), definition.longitude(), now).current().precipitation())
                .isEqualTo(com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Precipitation.NONE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void allToolScenesSeedTheirWeatherAndNotificationFixturesBeforeAnyLiveModelCall() throws Exception {
        SeedEvaluationScene[] scenes;
        try (var stream = new ClassPathResource("recall/evaluation/datasets/tool-scenes.json").getInputStream()) {
            scenes = mapper.readValue(stream, SeedEvaluationScene[].class);
        }
        assertThat(scenes).hasSize(18);
        for (var scene : scenes) {
            var seeded = seeder.resetAndSeed(scene);
            var execution = seeder.createExecution(scene, seeded, "fake-tool-check");
            assertThat(inputs.prepare(execution.executionId()).origin()).isNotNull();
            var origin = scene.seed().places().get(scene.event().placeKey());
            var placeId = seeded.placeIds().get(scene.event().placeKey());
            var checked = tools.checkVisitConditions(new com.ktc.chungnam3.remembrall.recall.tool.RecallToolScope(
                    seeded.memberId(), placeId, origin.latitude(), origin.longitude()), List.of(placeId));
            assertThat(checked.error()).isNull();
            assertThat(checked.places()).hasSize(1);
            assertThat(checked.weather().available()).isTrue();
            assertThat(checked.weather().current().precipitation()).isEqualTo(scene.weather() == null
                    ? com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Precipitation.NONE : scene.weather().precipitation());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE status='SENT'", Integer.class))
                    .isEqualTo(scene.recentNotifications().size());
        }
    }

    @Test
    void realPersistenceBuildsAllCandidateKindsAndIsolatesScenesWithOneSeedAndFreshExecutions() throws Exception {
        var scenes = scenes();
        var reordered = List.of(scenes.get(3), scenes.get(0), scenes.get(2), scenes.get(1));
        var output = directory.resolve("candidates.jsonl");
        var lines = runner.run(reordered, 3, "FAKE", "fixed-model", fake(input -> invocation(RecallAgentResult.noAction())), output);
        assertThat(lines).hasSize(12).allSatisfy(line -> {
            assertThat(line.verdict().passed()).isTrue();
            assertThat(line.failure()).isNull();
            assertThat(line.agentVersion()).isEqualTo("test-agent-v1");
            assertThat(line.seedTimeMillis()).isGreaterThanOrEqualTo(0);
            assertThat(line.candidatePreparationTimeMillis()).isGreaterThanOrEqualTo(0);
            assertThat(line.agentCallTimeMillis()).isGreaterThanOrEqualTo(0);
            assertThat(line.inputTokens()).isEqualTo(100);
            assertThat(line.outputTokens()).isEqualTo(10);
        });
        assertThat(lines).extracting(SeedEvaluationRunner.Line::executionId).doesNotHaveDuplicates();
        verify(seeder, times(4)).resetAndSeed(any());
        verify(embeddings, times(8)).embedDocument(anyString());
        verify(validator, never()).valid(any(), any(), any());
        var publicInput = lines.getFirst().input();
        assertThat(publicInput.origin().name()).isEqualTo("둔산 골목 로스터리");
        assertThat(publicInput.nearbyPersonalCandidates()).isEmpty();
        assertThat(publicInput.nearbyPublicCandidates()).hasSize(1);
        assertThat(publicInput.nearbyPublicCandidates().getFirst().distanceMeters()).isBetween(395.0, 405.0);
        assertThat(publicInput.nearbyPublicCandidates().getFirst().contents().getFirst().description())
                .contains("약 400m");
        var weekendInput = lines.get(3).input();
        assertThat(weekendInput.nearbyPersonalCandidates()).hasSize(1);
        assertThat(weekendInput.nearbyPersonalCandidates().getFirst().distanceMeters()).isBetween(295.0, 305.0);
        assertThat(weekendInput.nearbyPublicCandidates()).isEmpty();
        assertThat(weekendInput.origin().contents().getFirst().savedAt()).isEqualTo(Instant.parse("2026-07-10T03:00:00Z"));
        assertThat(weekendInput.situation().localDateTime()).isEqualTo(LocalDateTime.parse("2026-10-10T18:10:00"));
        assertThat(lines.get(6).input().semanticCandidates()).hasSize(1);
        assertThat(lines.get(6).input().semanticCandidates().getFirst().title()).contains("프렌치토스트");
        assertThat(lines.get(6).input().semanticCandidates().getFirst().source()).isEqualTo(ProposalSource.PERSONAL);
        assertThat(lines.get(9).input().situation().localDateTime()).isEqualTo(LocalDateTime.parse("2026-10-12T08:00:00"));
        for (int sceneIndex = 0; sceneIndex < reordered.size(); sceneIndex++) {
            int index = sceneIndex * 3;
            var first = lines.get(index);
            assertThat(lines.get(index + 1).input()).isEqualTo(first.input());
            assertThat(lines.get(index + 2).input()).isEqualTo(first.input());
            if (sceneIndex > 0) {
                var previous = lines.get(index - 3).input();
                String json = mapper.writeValueAsString(first.input());
                assertThat(json).doesNotContain(previous.origin().placeId().toString());
                assertThat(json).doesNotContain(previous.origin().contents().getFirst().contentId().toString());
                previous.nearbyPersonalCandidates().forEach(place -> assertThat(json).doesNotContain(place.placeId().toString()));
                previous.nearbyPublicCandidates().forEach(place -> assertThat(json).doesNotContain(place.placeId().toString()));
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM place", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recall_execution", Integer.class)).isEqualTo(3);
        var outputLines = Files.readAllLines(outputFor(lines.getFirst().runId()));
        assertThat(outputLines).hasSize(12);
        var json = mapper.readTree(outputLines.getFirst());
        assertThat(json.get("runId").asText()).isEqualTo(lines.getFirst().runId().toString());
        assertThat(json.get("runStartedAt").asText()).isEqualTo(lines.getFirst().runStartedAt().toString());
        assertThat(lines).extracting(SeedEvaluationRunner.Line::runId).containsOnly(lines.getFirst().runId());
        assertThat(lines).extracting(SeedEvaluationRunner.Line::runStartedAt).containsOnly(lines.getFirst().runStartedAt());
        assertThat(json.get("input")).isEqualTo(mapper.valueToTree(publicInput));
        assertThat(json.get("rawResponse").asText()).isNotBlank();
        assertThat(json.at("/verdict/passed").asBoolean()).isTrue();
        assertThat(json.get("failure").isNull()).isTrue();
        assertThat(json.get("trace").size()).isEqualTo(3);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.trace()).extracting(entry -> entry.step().name()).containsExactly(
                    "CANDIDATE_PREPARATION", "MODEL_CALL", "VALIDATION");
            assertThat(line.trace()).allSatisfy(entry -> {
                assertThat(entry.executionId()).isEqualTo(line.executionId());
                assertThat(entry.durationMs()).isGreaterThanOrEqualTo(0);
            });
            assertThat(line.trace().get(1).inputTokens()).isEqualTo(100);
            assertThat(line.trace().get(1).outputTokens()).isEqualTo(10);
            assertThat(line.trace().get(1).detail().has("agentInput")).isFalse();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recall_execution_trace", Integer.class)).isZero();
        var console = new ByteArrayOutputStream();
        SeedEvaluationRunner.printSummary(lines, new PrintStream(console, true, StandardCharsets.UTF_8));
        assertThat(console.toString(StandardCharsets.UTF_8)).contains("validityPassRate=100.0%", "NO_ACTION:12",
                "avgSeed=", "avgCandidatePreparation=", "avgAgentCall=", "avgInputTokens=100.0",
                "avgOutputTokens=10.0", "not decision quality");
    }

    @Test
    void proposesAreJudgedByTheRealValidatorIncludingPublicAndPlacelessCandidates() throws Exception {
        var lines = runner.run(scenes(), 1, "FAKE", "fixed-model", fake(input -> {
            if (!input.semanticCandidates().isEmpty()) {
                var content = input.semanticCandidates().getFirst();
                return invocation(proposal(ProposalType.CONTENT,
                        new ProposalPayload.Item(content.contentId(), null, content.source())));
            }
            if (!input.nearbyPublicCandidates().isEmpty()) {
                var place = input.nearbyPublicCandidates().getFirst();
                return invocation(proposal(ProposalType.PLACE, new ProposalPayload.Item(
                        place.contents().getFirst().contentId(), place.placeId(), ProposalSource.PUBLIC)));
            }
            var place = input.nearbyPersonalCandidates().getFirst();
            return invocation(proposal(ProposalType.COURSE, originItem(input), new ProposalPayload.Item(
                    place.contents().getFirst().contentId(), place.placeId(), ProposalSource.PERSONAL)));
        }), directory.resolve("proposals.jsonl"));
        assertThat(lines).allSatisfy(line -> assertThat(line.verdict().passed()).isTrue());
        assertThat(lines).extracting(line -> line.result().proposalType())
                .containsExactly(ProposalType.COURSE, ProposalType.COURSE, ProposalType.CONTENT, ProposalType.PLACE);
        verify(validator, times(4)).valid(any(), any(), any());
        var last = lines.getLast();
        UUID memberId = jdbc.queryForObject("SELECT t.member_id FROM trigger t JOIN recall_execution r ON r.trigger_id=t.id WHERE r.id=?",
                UUID.class, last.executionId());
        verify(validator).valid(eq(memberId), eq(last.input().origin().placeId()), eq(last.result()));
    }

    @Test
    void existingButUnlinkedPlaceIsRejectedByTheRealValidator() throws Exception {
        var lines = runner.run(List.of(scenes().getFirst()), 1, "FAKE", "fixed-model", fake(input ->
                invocation(proposal(ProposalType.PLACE, new ProposalPayload.Item(
                        input.origin().contents().getFirst().contentId(),
                        input.nearbyPersonalCandidates().getFirst().placeId(), ProposalSource.PERSONAL)))),
                directory.resolve("unlinked.jsonl"));
        assertThat(lines.getFirst().verdict().passed()).isFalse();
        assertThat(lines.getFirst().failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.VALIDATION);
        assertThat(lines.getFirst().failure().message()).contains("INVALID_PROPOSAL");
        assertThat(lines.getFirst().trace().getLast().success()).isFalse();
        assertThat(lines.getFirst().trace().getLast().step().name()).isEqualTo("VALIDATION");
        verify(validator).valid(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noActionRejectsProposalTypeOrNonemptyItems(boolean withType) throws Exception {
        var lines = runner.run(List.of(scenes().getFirst()), 1, "FAKE", "fixed-model", fake(input -> invocation(
                new RecallAgentResult(RecallExecutionResult.NO_ACTION, withType ? ProposalType.PLACE : null,
                        withType ? null : new ProposalPayload(List.of(originItem(input))), null, null, null))),
                directory.resolve("no-action-shape.jsonl"));
        assertThat(lines.getFirst().verdict().errors()).containsExactly("INVALID_NO_ACTION_SHAPE");
        verify(validator, never()).valid(any(), any(), any());
    }

    @Test
    void seedFailuresAreWrittenForEveryRepetitionAndFollowingScenesStillRun() throws Exception {
        doThrow(new IllegalStateException("injected seed failure")).doCallRealMethod().when(seeder).resetAndSeed(any());
        var path = directory.resolve("seed-failures.jsonl");
        var lines = runner.run(scenes().subList(0, 2), 2, "FAKE", "fixed-model",
                fake(input -> invocation(RecallAgentResult.noAction())), path);
        assertThat(lines).hasSize(4);
        assertThat(lines.subList(0, 2)).allSatisfy(line -> {
            assertThat(line.failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.SEED);
            assertThat(line.failure().message()).contains("injected seed failure");
            assertThat(line.input()).isNull();
            assertThat(line.agentCallTimeMillis()).isNull();
        });
        assertThat(lines.subList(2, 4)).allSatisfy(line -> assertThat(line.verdict().passed()).isTrue());
        assertThat(Files.readAllLines(outputFor(lines.getFirst().runId()))).hasSize(4);
        verify(inputs, times(2)).prepare(any());
    }

    @Test
    void embeddingFailureIsASeedFailureRatherThanSilentlyLosingSemanticCandidates() throws Exception {
        when(embeddings.embedDocument(anyString())).thenThrow(new IllegalStateException("fake embedding unavailable"));
        var lines = runner.run(List.of(scenes().getFirst()), 2, "FAKE", "fixed-model",
                fake(input -> invocation(RecallAgentResult.noAction())), directory.resolve("embedding-failures.jsonl"));
        assertThat(lines).hasSize(2).allSatisfy(line -> {
            assertThat(line.failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.SEED);
            assertThat(line.failure().message()).contains("embedding is incomplete");
        });
        verify(inputs, never()).prepare(any());
    }

    @Test
    void candidatePreparationFailureKeepsExecutionIdAndDoesNotStopFollowingRepetitions() throws Exception {
        doThrow(new IllegalStateException("candidate failure")).doCallRealMethod().when(inputs).prepare(any());
        var lines = runner.run(List.of(scenes().getFirst()), 2, "FAKE", "fixed-model",
                fake(input -> invocation(RecallAgentResult.noAction())), directory.resolve("preparation-failures.jsonl"));
        var failed = lines.getFirst();
        assertThat(failed.failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.CANDIDATE_PREPARATION);
        assertThat(failed.failure().message()).contains("candidate failure");
        assertThat(failed.executionId()).isNotNull();
        assertThat(failed.candidatePreparationTimeMillis()).isGreaterThanOrEqualTo(0);
        assertThat(failed.agentCallTimeMillis()).isNull();
        assertThat(lines.getLast().verdict().passed()).isTrue();
    }

    @Test
    void modelCallAndConversionFailuresKeepRawResponsesTokensAndStageMessages() throws Exception {
        var calls = new AtomicInteger();
        var path = directory.resolve("model-failures.jsonl");
        var lines = runner.run(List.of(scenes().getFirst()), 6, "FAKE", "fixed-model", fake(input -> {
            return switch (calls.incrementAndGet()) {
                case 1 -> throw new IOException("provider unavailable");
                case 2 -> throw new LlmRecallAgent.CallException(new IllegalArgumentException("bad JSON"),
                        new LlmRecallAgent.Invocation("{broken", null, 2.0, 120, 12));
                case 3 -> new LlmRecallAgent.Invocation("{}", null, 1.0, 110, 11);
                case 4 -> null;
                case 5 -> new LlmRecallAgent.Invocation("", null, 1.0, 100, 10);
                default -> invocation(RecallAgentResult.noAction());
            };
        }), path);
        assertThat(lines).hasSize(6);
        assertThat(lines.subList(0, 5)).extracting(line -> line.failure().stage()).containsExactly(
                SeedEvaluationRunner.Stage.MODEL_CALL, SeedEvaluationRunner.Stage.CONVERSION,
                SeedEvaluationRunner.Stage.CONVERSION, SeedEvaluationRunner.Stage.MODEL_CALL,
                SeedEvaluationRunner.Stage.MODEL_CALL);
        assertThat(lines.getFirst().failure().message()).contains("provider unavailable");
        assertThat(lines.get(1).failure().message()).contains("bad JSON");
        assertThat(lines.get(1).rawResponse()).isEqualTo("{broken");
        assertThat(lines.get(1).inputTokens()).isEqualTo(120);
        assertThat(lines.get(1).outputTokens()).isEqualTo(12);
        assertThat(lines.get(1).trace().getLast().step().name()).isEqualTo("MODEL_CALL");
        assertThat(lines.get(1).trace().getLast().success()).isFalse();
        assertThat(lines.get(1).trace().getLast().errorMessage()).contains("bad JSON");
        assertThat(lines.get(1).trace().getLast().inputTokens()).isEqualTo(120);
        assertThat(lines.getLast().verdict().passed()).isTrue();
        assertThat(Files.readAllLines(outputFor(lines.getFirst().runId()))).hasSize(6);
        var console = new ByteArrayOutputStream();
        SeedEvaluationRunner.printSummary(lines, new PrintStream(console, true, StandardCharsets.UTF_8));
        assertThat(console.toString(StandardCharsets.UTF_8)).contains("MODEL_CALL:3", "CONVERSION:2", "NO_ACTION:1", "NO_RESULT:5");
    }

    @Test
    void validationExceptionsAreRecorded() throws Exception {
        doThrow(new IllegalStateException("validator failure")).when(validator).valid(any(), any(), any());
        var lines = runner.run(List.of(scenes().getFirst()), 1, "FAKE", "fixed-model",
                fake(input -> invocation(proposal(ProposalType.PLACE, originItem(input)))), directory.resolve("validator-failures.jsonl"));
        assertThat(lines.getFirst().failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.VALIDATION);
        assertThat(lines.getFirst().failure().message()).contains("validator failure");
        assertThat(lines.getFirst().result()).isNotNull();
    }

    @Test
    void contributorWithoutConsentIsExcludedAndGoldenFieldsAreOnlyRead() throws Exception {
        var scene = scenes().getLast();
        var contributor = scene.seed().contributors().getFirst();
        var seed = new SeedEvaluationScene.Seed(scene.seed().places(), scene.seed().personalSaves(),
                List.of(new SeedEvaluationScene.Contributor(false, contributor.saves())));
        var reference = mapper.readTree("{\"result\":\"NO_ACTION\"}");
        var annotated = new SeedEvaluationScene(scene.id(), "tune", "tool", seed, scene.event(), reference,
                mapper.readTree("\"not an instruction or expectation\""));
        assertThat(annotated.reference()).isEqualTo(reference);
        var lines = runner.run(List.of(annotated), 1, "FAKE", "fixed-model", fake(input -> {
            assertThat(input.nearbyPublicCandidates()).isEmpty();
            assertThat(mapper.writeValueAsString(input)).doesNotContain("not an instruction or expectation",
                    "judgeNotes", "reference", "split", "kind");
            return invocation(proposal(ProposalType.PLACE, originItem(input)));
        }), directory.resolve("consent.jsonl"));
        assertThat(lines.getFirst().verdict().passed()).isTrue();
        assertThat(lines.getFirst().split()).isEqualTo("tune");
        assertThat(lines.getFirst().kind()).isEqualTo("tool");
        var row = mapper.readTree(Files.readAllLines(outputFor(lines.getFirst().runId())).getFirst());
        assertThat(row.get("split").asText()).isEqualTo("tune");
        assertThat(row.get("kind").asText()).isEqualTo("tool");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_consent WHERE consent_type='PUBLIC_CANDIDATE_CONTRIBUTION' AND withdrawn_at IS NULL", Integer.class))
                .isZero();
    }

    @Test
    void sharedPlaceKeysCreateOnePlaceAndKeepPerContentDescriptions() throws Exception {
        var scene = scenes().getFirst();
        var cafe = scene.seed().personalSaves().getFirst();
        var meal = scene.seed().personalSaves().getLast();
        var linkedMeal = new SeedEvaluationScene.Save(meal.videoId(), meal.title(), meal.summary(), meal.category(),
                meal.savedAt(), List.of(new SeedEvaluationScene.PlaceLink("cafe", "식사 후 같은 카페에 들른다."),
                meal.places().getFirst()));
        var seed = new SeedEvaluationScene.Seed(scene.seed().places(), List.of(cafe, linkedMeal), List.of());
        var lines = runner.run(List.of(new SeedEvaluationScene(scene.id(), scene.split(), scene.kind(), seed, scene.event(), null, null)),
                1, "FAKE", "fixed-model", fake(input -> invocation(RecallAgentResult.noAction())), directory.resolve("shared.jsonl"));
        assertThat(lines.getFirst().input().origin().contents()).hasSize(2);
        assertThat(lines.getFirst().input().origin().contents()).extracting(content -> content.description())
                .contains("식사 후 같은 카페에 들른다.", cafe.places().getFirst().description());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM place", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_place", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trigger", Integer.class)).isEqualTo(2);
    }

    @Test
    void refusesToClearOrSeedAnyConnectionOtherThanItsOwnTestcontainer() throws Exception {
        var database = mock(PostgreSQLContainer.class);
        when(database.isRunning()).thenReturn(true);
        when(database.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:12345/test");
        var guardedJdbc = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:5432/development");
        when(guardedJdbc.execute(any(ConnectionCallback.class))).thenAnswer(call ->
                ((ConnectionCallback<?>) call.getArgument(0)).doInConnection(connection));
        var guarded = new SeedEvaluationSeeder(database, guardedJdbc, null, null, null, null, null,
                null, null, mock(PlatformTransactionManager.class), java.time.Clock.systemUTC(), new FakeWeatherClient());
        assertThatThrownBy(() -> guarded.resetAndSeed(scenes().getFirst()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside this Testcontainer");
        verify(guardedJdbc, never()).update(anyString());
    }

    @Test
    void startupFailuresAreRecordedForAllScenesAndRepetitions() throws Exception {
        var path = directory.resolve("startup-failures.jsonl");
        var lines = SeedEvaluationRunner.recordFailure(scenes(), 3, "FAKE", "fixed-model",
                SeedEvaluationRunner.Stage.SEED, new IllegalStateException("container startup failed"), path);
        assertThat(lines).hasSize(12).allSatisfy(line -> {
            assertThat(line.verdict().passed()).isFalse();
            assertThat(line.failure().stage()).isEqualTo(SeedEvaluationRunner.Stage.SEED);
            assertThat(line.failure().message()).contains("container startup failed");
        });
        var outputLines = Files.readAllLines(outputFor(lines.getFirst().runId()));
        assertThat(outputLines).hasSize(12);
        assertThat(outputLines.stream().map(mapper::readTree)).allSatisfy(row -> {
            assertThat(row.get("runId").asText()).isEqualTo(lines.getFirst().runId().toString());
            assertThat(row.get("runStartedAt").asText()).isEqualTo(lines.getFirst().runStartedAt().toString());
        });
    }

    @Test
    void sameAgentVersionCreatesSeparateResultFilesForEveryExecution() throws Exception {
        Path template = directory.resolve("repeat.jsonl");
        Files.writeString(template, "previous results\n");
        var selected = List.of(scenes().getFirst());
        var agent = fake(input -> invocation(RecallAgentResult.noAction()));
        var first = runner.run(selected, 2, "FAKE", "fixed-model", agent, template);
        Path firstPath = outputFor(first.getFirst().runId());
        var firstRows = Files.readAllLines(firstPath);
        var second = runner.run(selected, 2, "FAKE", "fixed-model", agent, template);
        Path secondPath = outputFor(second.getFirst().runId());
        assertThat(first.getFirst().agentVersion()).isEqualTo(second.getFirst().agentVersion());
        assertThat(first.getFirst().runId()).isNotEqualTo(second.getFirst().runId());
        assertThat(secondPath).isNotEqualTo(firstPath);
        assertThat(secondPath.getParent()).isNotEqualTo(firstPath.getParent());
        assertThat(firstPath.getFileName().toString()).isEqualTo("repeat.jsonl");
        assertThat(secondPath.getFileName().toString()).isEqualTo("repeat.jsonl");
        assertThat(firstRows).hasSize(2);
        assertThat(Files.readAllLines(firstPath)).containsExactlyElementsOf(firstRows);
        assertThat(Files.readAllLines(secondPath)).hasSize(2).allSatisfy(row ->
                assertThat(mapper.readTree(row).get("runId").asText()).isEqualTo(second.getFirst().runId().toString()));
        assertThat(Files.readString(template)).isEqualTo("previous results\n");
    }

    @Test
    void multipleModelsAndFailureRecordsShareOneFileWithinTheSameExecution() throws Exception {
        Path path;
        UUID runId;
        var selected = List.of(scenes().getFirst());
        try (var run = EvaluationRun.start(directory.resolve("models.jsonl"))) {
            path = run.output();
            runId = run.runId();
            var success = runner.run(selected, 1, "FAKE", "first-model",
                    fake(input -> invocation(RecallAgentResult.noAction())), run);
            var failed = SeedEvaluationRunner.recordFailure(selected, 2, "FAKE", "second-model",
                    SeedEvaluationRunner.Stage.MODEL_CALL, new IllegalStateException("model unavailable"), run);
            assertThat(success.getFirst().runId()).isEqualTo(runId);
            assertThat(failed).allSatisfy(line -> {
                assertThat(line.runId()).isEqualTo(runId);
                assertThat(line.runStartedAt()).isEqualTo(run.startedAt());
            });
        }
        var rows = Files.readAllLines(path).stream().map(mapper::readTree).toList();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("runId").asText()).containsOnly(runId.toString());
        assertThat(rows).extracting(row -> row.get("model").asText())
                .containsExactly("first-model", "second-model", "second-model");
    }

    @Test
    void sceneResourceHasNoExpectationsAndSeoulTimestampsSupportExplicitAndLocalForms() throws Exception {
        try (var stream = new ClassPathResource("recall/evaluation/seed-scenes.json").getInputStream()) {
            var tree = mapper.readTree(stream);
            assertThat(tree.size()).isEqualTo(4);
            tree.forEach(scene -> assertThat(scene.has("expectation")).isFalse());
        }
        assertThat(SeedEvaluationSeeder.seoulInstant("2026-10-10T18:10:00"))
                .isEqualTo(SeedEvaluationSeeder.seoulInstant("2026-10-10T18:10:00+09:00"));
        assertThatThrownBy(() -> SeedEvaluationSeeder.seoulInstant("2026-10-10T18:10:00Z"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Asia/Seoul");
    }

    private List<SeedEvaluationScene> scenes() throws IOException {
        try (var stream = new ClassPathResource("recall/evaluation/seed-scenes.json").getInputStream()) {
            return List.of(mapper.readValue(stream, SeedEvaluationScene[].class));
        }
    }

    private Path outputFor(UUID runId) throws IOException {
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

    private RecallEvaluationRunner.EvaluationAgent fake(InvocationProvider provider) {
        return new RecallEvaluationRunner.EvaluationAgent() {
            @Override
            public String version() {
                return "test-agent-v1";
            }

            @Override
            public LlmRecallAgent.Invocation invoke(RecallAgentInput input) throws Exception {
                return provider.invoke(input);
            }
        };
    }

    private LlmRecallAgent.Invocation invocation(RecallAgentResult result) {
        return new LlmRecallAgent.Invocation(mapper.writeValueAsString(result), result, 1.5, 100, 10);
    }

    private RecallAgentResult proposal(ProposalType type, ProposalPayload.Item... items) {
        return RecallAgentResult.propose(type, new ProposalPayload(List.of(items)), "Decision", "Title", "Body");
    }

    private ProposalPayload.Item originItem(RecallAgentInput input) {
        return new ProposalPayload.Item(input.origin().contents().getFirst().contentId(),
                input.origin().placeId(), ProposalSource.PERSONAL);
    }

    @FunctionalInterface
    private interface InvocationProvider {
        LlmRecallAgent.Invocation invoke(RecallAgentInput input) throws Exception;
    }
}
