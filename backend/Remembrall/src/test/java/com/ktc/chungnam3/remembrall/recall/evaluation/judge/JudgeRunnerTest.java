package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.ktc.chungnam3.remembrall.recall.evaluation.EvaluationRun;
import com.ktc.chungnam3.remembrall.recall.evaluation.SeedEvaluationScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeRunnerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String CAFE = "11111111-1111-1111-1111-111111111111";
    private static final String MEAL = "22222222-2222-2222-2222-222222222222";
    private static final String CAFE_VIDEO = "33333333-3333-3333-3333-333333333333";
    private static final String MEAL_VIDEO = "44444444-4444-4444-4444-444444444444";
    private static final JudgeCriteria CRITERIA = JudgeCriteria.fromText("test:inline", """
            ## 필수 조건
            테스트용 조건 1, 2, 3, 4, 6, 7
            ## 평가 적용 원칙
            평일 오전이나 심야에 제안하는 것은 그 자체로 위반이 아니다.
            ## 채점 기준
            |점수|라벨|reference|judgeNotes|goodChoices|
            """);
    @TempDir Path directory;

    @Test
    void serverFailureSkipsJudgeAndRecordsBadConditionFive() throws Exception {
        var calls = new AtomicInteger();
        var row = row();
        ((ObjectNode) row.path("verdict")).put("passed", false).putArray("errors").add("INVALID_PROPOSAL");
        var line = run(List.of(row), scene(false, null), (system, user) -> {
            calls.incrementAndGet(); throw new AssertionError("Must not call Judge");
        }).lines().getFirst();
        assertThat(calls).hasValue(0);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.BAD);
        assertThat(line.score()).isEqualTo(1);
        assertThat(line.violations()).extracting(JudgeRunner.Violation::conditionNumber).containsExactly(5);
        assertThat(line.rawJudgeResponse()).isNull();
        assertThat(line.comparison()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEED", "CANDIDATE_PREPARATION", "MODEL_CALL", "CONVERSION"})
    void sourceFailureIsAgentFailedWithoutCallingJudgeAndPreservesOriginalFailure(String stage) throws Exception {
        var row = row();
        ((ObjectNode) row.path("verdict")).put("passed", false);
        row.putNull("result");
        String message = "agent offline\n원래 오류 메시지";
        row.putObject("failure").put("stage", stage).put("message", message);
        var result = run(List.of(row), scene(false, null), (system, user) -> {
            throw new AssertionError("No Judge call without an agent result");
        });
        var line = result.lines().getFirst();
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.AGENT_FAILED);
        assertThat(line.score()).isNull();
        assertThat(line.violations()).isEmpty();
        assertThat(line.failure()).isEqualTo(new JudgeRunner.Failure(stage, message));
        assertThat(line.rawJudgeResponse()).isNull();
        assertThat(line.judgeTimeMillis()).isZero();
        assertThat(line.inputTokens()).isNull();
        assertThat(line.outputTokens()).isNull();
        assertThat(MAPPER.readValue(Files.readAllLines(result.judgedJsonl()).getFirst(), JudgeRunner.Line.class).failure())
                .isEqualTo(line.failure());
        assertThat(HumanLabelCsv.parse(Files.readString(result.humanCsv())).get(1).get(6)).isEqualTo("AGENT_FAILED");
    }

    @Test
    void agentFailureIsRecognizedBeforeCheckingMissingValidationOrInput() throws Exception {
        var row = row();
        row.remove("verdict");
        row.remove("input");
        row.remove("result");
        row.putObject("failure").put("stage", "SEED").put("message", "database offline");

        var line = run(List.of(row), scene(false, null), (system, user) -> {
            throw new AssertionError("Must not call Judge for a seed failure");
        }).lines().getFirst();

        assertThat(line.label()).isEqualTo(JudgeRunner.Label.AGENT_FAILED);
        assertThat(line.failure()).isEqualTo(new JudgeRunner.Failure("SEED", "database offline"));
    }

    @Test
    void bothFailureLabelsAreExcludedFromQualityAndReportedSeparatelyForReliability() throws Exception {
        var agentFailure = row();
        agentFailure.putNull("result");
        agentFailure.putObject("failure").put("stage", "MODEL_CALL").put("message", "agent timeout");
        var judgeFailure = row();
        judgeFailure.put("repetition", 2);
        var success = row();
        success.put("repetition", 3);
        var calls = new AtomicInteger();
        var result = run(List.of(agentFailure, judgeFailure, success), scene(false, null), (system, user) ->
                response(calls.incrementAndGet() <= 2 ? "not JSON" : "{\"reasoning\":\"확인\",\"violations\":[]}"));

        assertThat(calls).hasValue(3);
        assertThat(result.lines()).extracting(JudgeRunner.Line::label)
                .containsExactly(JudgeRunner.Label.AGENT_FAILED, JudgeRunner.Label.JUDGE_FAILED, JudgeRunner.Label.GOOD);
        assertThat(Files.readString(result.report())).contains(
                "## 품질 점수", "| 전체 | 3 | 1 | 5.000 | 1 | 0 | 0 | 1 | 1 | 0 |",
                "| tune | 3 | 1 | 5.000 | 1 | 0 | 0 | 1 | 1 | 0 |",
                "| judgment | 3 | 1 | 5.000 | 1 | 0 | 0 | 1 | 1 | 0 |",
                "## 신뢰성", "전체 실행 수: 3", "AGENT_FAILED: 1 / 3 (33.3%)", "JUDGE_FAILED: 1 / 3 (33.3%)",
                "| AGENT_FAILED | MODEL_CALL | 1 |", "| JUDGE_FAILED | PARSING | 1 |",
                "| AGENT_FAILED | scene | 1 | 1 | MODEL_CALL | agent timeout |",
                "| JUDGE_FAILED | scene | 2 | 2 | PARSING |", "1: AGENT_FAILED, 2: JUDGE_FAILED, 3: GOOD");
    }

    @Test
    void modelViolationOverridesAnOtherwiseMatchingGoodChoice() throws Exception {
        var calls = new AtomicInteger();
        var line = run(List.of(row()), scene(false, null), (system, user) -> {
            calls.incrementAndGet();
            return response("""
                {"reasoning":"근거 없는 영업 단정","violations":[{"conditionNumber":3,"evidence":"지금 영업 중이라는 근거가 없다"}]}
                """);
        }).lines().getFirst();
        assertThat(calls).hasValue(1);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.BAD);
        assertThat(line.score()).isEqualTo(1);
        assertThat(line.comparison().status()).isEqualTo("NOT_RUN");
        assertThat(line.comparison().matched()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void orderedChoiceChecksSequenceAndUnorderedChoiceAllowsReversal(boolean ordered) throws Exception {
        var row = row();
        var items = (tools.jackson.databind.node.ArrayNode) row.at("/result/proposalPayload/items");
        var first = items.remove(0);
        items.add(first);
        var line = run(List.of(row), scene(ordered, null), good()).lines().getFirst();
        assertThat(line.label()).isEqualTo(ordered ? JudgeRunner.Label.ACCEPTABLE : JudgeRunner.Label.GOOD);
        assertThat(line.comparison().matched()).isEqualTo(!ordered);
    }

    @Test
    void specifiedVideosAndTheirPlaceAssociationsMustMatch() throws Exception {
        var exact = scene(false, List.of("eval-seed-cafe", "eval-seed-meal"));
        assertThat(run(List.of(row()), exact, good()).lines().getFirst().label()).isEqualTo(JudgeRunner.Label.GOOD);
        var mismatched = scene(false, List.of("eval-seed-meal", "eval-seed-cafe"));
        assertThat(run(List.of(row()), mismatched, good()).lines().getFirst().label())
                .isEqualTo(JudgeRunner.Label.ACCEPTABLE);
    }

    @Test
    void contentChoiceMatchesVideoWithoutAPlace() throws Exception {
        var scene = withChoices(List.of(new SeedEvaluationScene.GoodChoice("PROPOSE", "CONTENT", null, false,
                List.of("eval-seed-cafe"))));
        var row = row();
        ((ObjectNode) row.path("result")).put("proposalType", "CONTENT");
        ((ObjectNode) row.path("result")).set("proposalPayload", MAPPER.readTree(
                "{\"items\":[{\"contentId\":\"" + CAFE_VIDEO + "\",\"placeId\":null,\"source\":\"PERSONAL\"}]}"));
        assertThat(run(List.of(row), scene, good()).lines().getFirst().label()).isEqualTo(JudgeRunner.Label.GOOD);
    }

    @Test
    void noActionMatchesNoActionWithoutIdMapping() throws Exception {
        var row = row();
        row.set("result", MAPPER.readTree("{\"result\":\"NO_ACTION\",\"proposalType\":null,\"proposalPayload\":null}"));
        var scene = withChoices(List.of(new SeedEvaluationScene.GoodChoice("NO_ACTION", null, null, false, null)));
        var line = run(List.of(row), scene, good()).lines().getFirst();
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.GOOD);
        assertThat(line.comparison().matchedIndexes()).containsExactly(0);
        assertThat(line.comparison().matchedChoices()).containsExactlyElementsOf(scene.goodChoices());
    }

    @Test
    void unmappedIdIsExplicitlyUncomparableNotAnInventedMatch() throws Exception {
        var row = row();
        String unmappedId = UUID.randomUUID().toString();
        ((ObjectNode) row.at("/result/proposalPayload/items/0")).put("contentId", unmappedId);
        var result = run(List.of(row), scene(false, null), good());
        var line = result.lines().getFirst();
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.UNCOMPARABLE);
        assertThat(line.score()).isNull();
        assertThat(line.comparison().status()).isEqualTo("UNCOMPARABLE");
        assertThat(line.comparison().matched()).isNull();
        assertThat(line.comparison().errors().getFirst()).contains("Unmapped contentId");
        assertThat(Files.readString(result.report())).contains("UNCOMPARABLE 라벨: 1", "## 비교 불가 상세",
                unmappedId, "| scene | 1 | UNCOMPARABLE |", "| 전체 | 1 | 0 | N/A |");
    }

    @Test
    void uncomparableIsExcludedFromOverallSplitAndKindAverages() throws Exception {
        var unmatched = row();
        ((ObjectNode) unmatched.at("/result/proposalPayload/items/0")).put("placeId", UUID.randomUUID().toString());
        var result = run(List.of(row(), unmatched), scene(false, null), good());
        assertThat(result.lines()).extracting(JudgeRunner.Line::label)
                .containsExactly(JudgeRunner.Label.GOOD, JudgeRunner.Label.UNCOMPARABLE);
        assertThat(Files.readString(result.report())).contains(
                "| 전체 | 2 | 1 | 5.000 | 1 | 0 | 0 | 0 | 0 | 1 |",
                "| tune | 2 | 1 | 5.000 | 1 | 0 | 0 | 0 | 0 | 1 |",
                "| judgment | 2 | 1 | 5.000 | 1 | 0 | 0 | 0 | 0 | 1 |");
    }

    @Test
    void unmappedIdIsStillRecordedWhenModelFindsAViolation() throws Exception {
        var row = row();
        ((ObjectNode) row.at("/result/proposalPayload/items/0")).put("placeId", UUID.randomUUID().toString());
        var line = run(List.of(row), scene(false, null), (system, user) -> response(
                "{\"reasoning\":\"없는 장소\",\"violations\":[{\"conditionNumber\":1,\"evidence\":\"입력에 없는 장소를 선택\"}]}"))
                .lines().getFirst();
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.BAD);
        assertThat(line.comparison().status()).isEqualTo("UNCOMPARABLE");
    }

    @Test
    void silenceAndProposalMismatchCountsAndRepetitionConsistencyAreReported() throws Exception {
        var noActionScene = withChoices(List.of(new SeedEvaluationScene.GoodChoice("NO_ACTION", null, null, false, null)));
        var first = row();
        var second = row();
        second.put("repetition", 2).set("result", MAPPER.readTree("{\"result\":\"NO_ACTION\"}"));
        var result = run(List.of(first, second), noActionScene, good());
        assertThat(result.lines().getFirst().silenceExpectedButProposed()).isTrue();
        assertThat(Files.readString(result.report())).contains("침묵만 goodChoices인데 제안: 1", "1: ACCEPTABLE, 2: GOOD | 아니오");
        var proposal = run(List.of(second), scene(false, null), good());
        assertThat(proposal.lines().getFirst().proposalExpectedButSilent()).isTrue();
    }

    @Test
    void fallbackCanResolveContentAndPlaceFromActualToolResponse() throws Exception {
        var row = row();
        ((tools.jackson.databind.node.ArrayNode) row.at("/input/nearbyPersonalCandidates")).removeAll();
        var tool = row.putArray("trace").addObject().put("step", "TOOL_CALL").put("name", "search");
        tool.putObject("detail").putObject("response").putArray("places").addObject()
                .put("placeId", MEAL).put("name", "식당").putArray("contents").addObject()
                .put("contentId", MEAL_VIDEO).put("title", "식당 영상");
        assertThat(run(List.of(row), scene(false, null), good()).lines().getFirst().label()).isEqualTo(JudgeRunner.Label.GOOD);
    }

    @Test
    void explicitSeedIdsTakePriorityOverNameAndTitleFallback() throws Exception {
        var row = row();
        ((ObjectNode) row.at("/input/origin")).put("name", "ambiguous place");
        ((ObjectNode) row.at("/input/origin/contents/0")).put("title", "ambiguous content");
        var ids = row.putObject("seedIds");
        ids.putObject("places").put("cafe", CAFE).put("meal", MEAL);
        ids.putObject("contents").put("eval-seed-cafe", CAFE_VIDEO).put("eval-seed-meal", MEAL_VIDEO);
        assertThat(run(List.of(row), scene(false, null), good()).lines().getFirst().label())
                .isEqualTo(JudgeRunner.Label.GOOD);
    }

    @Test
    void ambiguousNamesAndTitlesAreNotGuessed() throws Exception {
        var original = scene(false, null);
        var places = new java.util.LinkedHashMap<>(original.seed().places());
        places.put("duplicate", places.get("cafe"));
        var scene = new SeedEvaluationScene(original.id(), original.split(), original.kind(),
                new SeedEvaluationScene.Seed(places, original.seed().personalSaves(), List.of()),
                original.event(), null, null, original.goodChoices());
        assertThat(run(List.of(row()), scene, good()).lines().getFirst().comparison().status())
                .isEqualTo("UNCOMPARABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{}", "{\"reasoning\":\"\",\"violations\":[]}",
            "{\"reasoning\":\"x\",\"violations\":[{\"conditionNumber\":5,\"evidence\":\"x\"}]}",
            "{\"reasoning\":\"x\",\"violations\":null}", "{\"reasoning\":\"x\",\"violations\":[]} {}",
            "{\"reasoning\":\"x\",\"violations\":[],\"score\":5}", "```json\n{\"reasoning\":\"x\",\"violations\":[]}"})
    void invalidJudgeResponsesAreUnscoredFailures(String raw) throws Exception {
        var calls = new AtomicInteger();
        var run = run(List.of(row()), scene(false, null), (system, user) -> {
            calls.incrementAndGet();
            return response(raw);
        });
        var line = run.lines().getFirst();
        assertThat(calls).hasValue(2);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.JUDGE_FAILED);
        assertThat(line.score()).isNull();
        assertThat(line.failure().stage()).isEqualTo("PARSING");
        assertThat(line.rawJudgeResponse()).isEqualTo(raw);
        assertThat(line.inputTokens()).isEqualTo(200);
        assertThat(line.outputTokens()).isEqualTo(20);
        assertThat(line.judgeTimeMillis()).isEqualTo(25);
        assertThat(Files.readString(run.report())).contains("| 전체 | 1 | 0 | N/A |", "JUDGE_FAILED: 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{}", "{\"reasoning\":\"\",\"violations\":[]}",
            "{\"reasoning\":\"x\",\"violations\":[{\"conditionNumber\":5,\"evidence\":\"x\"}]}",
            "{\"reasoning\":\"x\",\"violations\":[{\"conditionNumber\":6,\"evidence\":\"\"}]}",
            "{\"reasoning\":\"x\",\"violations\":null}"})
    void parsingOrValidationFailureResendsTheSameRequestOnceAndCanRecover(String firstRaw) throws Exception {
        var calls = new AtomicInteger();
        var messages = new ArrayList<List<String>>();
        String valid = "{\"reasoning\":\"근거 범위 내 선택\",\"violations\":[]}";
        var line = run(List.of(row()), scene(false, null), (system, user) -> {
            messages.add(List.of(system, user));
            return response(calls.incrementAndGet() == 1 ? firstRaw : valid);
        }).lines().getFirst();

        assertThat(calls).hasValue(2);
        assertThat(messages.get(0)).isEqualTo(messages.get(1));
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.GOOD);
        assertThat(line.failure()).isNull();
        assertThat(line.rawJudgeResponse()).isEqualTo(valid);
        assertThat(line.inputTokens()).isEqualTo(200);
        assertThat(line.outputTokens()).isEqualTo(20);
        assertThat(line.judgeTimeMillis()).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(strings = {"```json\n{\"reasoning\":\"근거 확인\",\"violations\":[]}\n```",
            "```\r\n{\"reasoning\":\"근거 확인\",\"violations\":[]}\r\n```",
            " \n```JSON\n{\"reasoning\":\"근거 확인\",\"violations\":[]}\n``` \n",
            "```{\"reasoning\":\"근거 확인\",\"violations\":[]}```"})
    void stripsSurroundingCodeFencesBeforeParsingWithoutRetrying(String raw) throws Exception {
        var calls = new AtomicInteger();
        var line = run(List.of(row()), scene(false, null), (system, user) -> {
            calls.incrementAndGet();
            return response(raw);
        }).lines().getFirst();

        assertThat(calls).hasValue(1);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.GOOD);
        assertThat(line.reasoning()).isEqualTo("근거 확인");
        assertThat(line.rawJudgeResponse()).isEqualTo(raw);
    }

    @Test
    void emptyResponsesAreRetriedOnlyOnceAndUnknownUsageRemainsNull() throws Exception {
        var calls = new AtomicInteger();
        var line = run(List.of(row()), scene(false, null), (system, user) -> {
            calls.incrementAndGet();
            return new JudgeModel.Response(" ", 1, null, null);
        }).lines().getFirst();

        assertThat(calls).hasValue(2);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.JUDGE_FAILED);
        assertThat(line.failure().stage()).isEqualTo("PARSING");
        assertThat(line.judgeTimeMillis()).isEqualTo(2);
        assertThat(line.inputTokens()).isNull();
        assertThat(line.outputTokens()).isNull();
    }

    @Test
    void transportFailureOnRetryKeepsReceivedResponseAndUsageWithoutAThirdCall() throws Exception {
        var calls = new AtomicInteger();
        var line = run(List.of(row()), scene(false, null), (system, user) -> {
            if (calls.incrementAndGet() == 1) {
                return response("not JSON");
            }
            throw new java.io.IOException("fake offline on retry");
        }).lines().getFirst();

        assertThat(calls).hasValue(2);
        assertThat(line.label()).isEqualTo(JudgeRunner.Label.JUDGE_FAILED);
        assertThat(line.failure().stage()).isEqualTo("MODEL_CALL");
        assertThat(line.failure().message()).contains("fake offline on retry");
        assertThat(line.rawJudgeResponse()).isEqualTo("not JSON");
        assertThat(line.inputTokens()).isEqualTo(100);
        assertThat(line.outputTokens()).isEqualTo(10);
        assertThat(line.judgeTimeMillis()).isGreaterThanOrEqualTo(12.5);
    }

    @Test
    void modelCallFailuresAreRecordedAndFollowingRowsStillRun() throws Exception {
        var calls = new AtomicInteger();
        var run = run(List.of(row(), row()), scene(false, null), (system, user) -> {
            if (calls.incrementAndGet() == 1) { throw new java.io.IOException("fake offline"); }
            return response("{\"reasoning\":\"근거 범위 내 선택\",\"violations\":[]}");
        });
        assertThat(run.lines()).extracting(JudgeRunner.Line::label)
                .containsExactly(JudgeRunner.Label.JUDGE_FAILED, JudgeRunner.Label.GOOD);
        assertThat(run.lines().getFirst().failure().stage()).isEqualTo("MODEL_CALL");
        assertThat(run.lines().getFirst().failure().message()).contains("fake offline");
        assertThat(calls).hasValue(2);
        assertThat(Files.readString(run.report())).contains("| 전체 | 2 | 1 | 5.000 |");
    }

    @Test
    void promptUsesOnlyObservedInputAndToolsAndNeverGoldenAnnotations() throws Exception {
        var messages = new ArrayList<String>();
        var row = row();
        row.putArray("trace").addObject().put("step", "TOOL_CALL").put("name", "details")
                .put("success", true).putObject("detail").put("arguments", "details requested")
                .putObject("response").put("contentId", MEAL_VIDEO).put("title", "식당 영상")
                .put("summary", "실제로 조회한 휴무 정보");
        var run = run(List.of(row), scene(false, null), (system, user) -> {
            messages.add(system); messages.add(user); return response("{\"reasoning\":\"확인\",\"violations\":[]}");
        });
        assertThat(messages.getFirst()).contains("조건 1, 2, 3, 4, 6, 7", "평일 오전이나 심야에 제안하는 것은 그 자체로 위반이 아니다")
                .doesNotContain("|점수|", "goodChoices", "judgeNotes", "reference");
        assertThat(messages.get(1)).contains("카페", "식당", "영상 요약", "장소 소개 맥락", "2026-10-10T18:10:00",
                        "SATURDAY", "실제로 조회한 휴무 정보", "거리", "2000-01-01T09:00+09:00")
                .doesNotContain(CAFE, MEAL_VIDEO, "GOLDEN_SECRET", "NOTES_SECRET", "goodChoices");
        assertThat(run.lines().getFirst().inputTokens()).isEqualTo(100);
        assertThat(run.lines().getFirst().outputTokens()).isEqualTo(10);
    }

    @Test
    void malformedSourceLineIsNotDroppedAndReportCountsAllRows() throws Exception {
        Path input = directory.resolve("broken.jsonl");
        Files.writeString(input, "{broken\n" + MAPPER.writeValueAsString(row()) + "\n");
        var run = runner(good()).run(input, List.of(scene(false, null)));
        assertThat(run.lines()).hasSize(2);
        assertThat(run.lines().getFirst().failure().stage()).isEqualTo("INPUT");
        assertThat(Files.readAllLines(run.judgedJsonl())).hasSize(2);
    }

    @Test
    void outputIsNewEveryTimeAndSourceIsNeverOverwritten() throws Exception {
        Path input = directory.resolve("results.jsonl");
        String source = MAPPER.writeValueAsString(row()) + "\n";
        Files.writeString(input, source);
        var first = runner(good()).run(input, List.of(scene(false, null)));
        var second = runner(good()).run(input, List.of(scene(false, null)));
        assertThat(first.judgedJsonl()).isNotEqualTo(second.judgedJsonl());
        assertThat(first.judgedJsonl().getParent()).isNotEqualTo(second.judgedJsonl().getParent());
        for (var run : List.of(first, second)) {
            Path runDirectory = run.judgedJsonl().getParent();
            assertThat(runDirectory.getParent()).isEqualTo(input.getParent());
            assertThat(runDirectory.getFileName().toString())
                    .matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(?:-\\d+)?");
            assertThat(run.judgedJsonl().getFileName().toString()).isEqualTo("judged.jsonl");
            assertThat(run.report()).isEqualTo(runDirectory.resolve("judged.md"));
            assertThat(run.humanCsv()).isEqualTo(runDirectory.resolve("judged-human.csv"));
            try (var files = Files.list(runDirectory)) {
                assertThat(files.toList()).containsExactlyInAnyOrder(run.judgedJsonl(), run.report(), run.humanCsv());
            }
        }
        assertThat(Files.readString(input)).isEqualTo(source);
    }

    @Test
    void criteriaPathAndFullDocumentHashAreRecordedForEveryRowIncludingFailures() throws Exception {
        var calls = new AtomicInteger();
        var result = run(List.of(row(), row()), scene(false, null), (system, user) -> {
            if (calls.incrementAndGet() == 1) {
                throw new java.io.IOException("fake offline");
            }
            return response("{\"reasoning\":\"확인\",\"violations\":[]}");
        });

        assertThat(result.lines()).allSatisfy(line -> {
            assertThat(line.criteriaPath()).isEqualTo(CRITERIA.path());
            assertThat(line.criteriaSha256()).isEqualTo(CRITERIA.sha256());
        });
        for (String json : Files.readAllLines(result.judgedJsonl())) {
            var line = MAPPER.readValue(json, JudgeRunner.Line.class);
            assertThat(line.criteriaPath()).isEqualTo(CRITERIA.path());
            assertThat(line.criteriaSha256()).isEqualTo(CRITERIA.sha256());
        }
        assertThat(Files.readString(result.report())).contains(CRITERIA.path(), CRITERIA.sha256());
    }

    @Test
    void judgingResultsInARunDirectoryCreatesASiblingTimestampDirectory() throws Exception {
        Path input;
        String source = MAPPER.writeValueAsString(row()) + System.lineSeparator();
        try (var run = EvaluationRun.start(directory.resolve("results.jsonl"))) {
            run.write(row());
            input = run.output();
        }

        var result = runner(good()).run(input, List.of(scene(false, null)));

        assertThat(result.judgedJsonl().getParent().getParent()).isEqualTo(directory);
        assertThat(result.judgedJsonl().getParent()).isNotEqualTo(input.getParent());
        assertThat(result.lines().getFirst().label()).isEqualTo(JudgeRunner.Label.GOOD);
        assertThat(Files.readString(input)).isEqualTo(source);
    }

    @Test
    void goldenSetReadsGoodChoicesWithoutChangingLegacySceneLoading() throws Exception {
        try (var stream = new ClassPathResource("recall/evaluation/datasets/golden-scenes.json").getInputStream()) {
            var scenes = MAPPER.readValue(stream, SeedEvaluationScene[].class);
            assertThat(scenes).hasSize(30).allSatisfy(scene -> assertThat(scene.goodChoices()).isNotEmpty());
        }
        try (var stream = new ClassPathResource("recall/evaluation/seed-scenes.json").getInputStream()) {
            assertThat(MAPPER.readValue(stream, SeedEvaluationScene[].class)).hasSize(4)
                    .allSatisfy(scene -> assertThat(scene.goodChoices()).isEmpty());
        }
    }

    @Test
    void reportContainsExpectedToolCallRatesFromActualSourceTrace() throws Exception {
        var original = scene(false, null);
        var scene = new SeedEvaluationScene(original.id(), original.split(), "tool", original.seed(), original.event(),
                original.reference(), original.judgeNotes(), original.goodChoices(), null, List.of(),
                List.of("checkVisitConditions", "searchCandidates"));
        var row = row();
        row.withArray("trace").addObject().put("step", "TOOL_CALL").put("name", "checkVisitConditions");
        var result = run(List.of(row), scene, good());
        assertThat(Files.readString(result.report())).contains("## 도구 호출 지표",
                "| 확인 호출률 | 1 | 1 | 100.0% |", "| 검색 호출률 | 0 | 1 | 0.0% |");
    }

    @Test
    void judgeProviderAndModelHaveNoDefaultAndRequireExplicitConfiguration() {
        assertThatThrownBy(() -> new JudgeProperties(null, null, java.time.Duration.ofSeconds(60), null, null, 0.0, false).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> new JudgeProperties("openai", "", java.time.Duration.ofSeconds(60), "url", "key", 0.0, false).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model");
    }

    private JudgeRunner.Run run(List<? extends JsonNode> rows, SeedEvaluationScene scene, JudgeModel model) throws Exception {
        Path input = directory.resolve(UUID.randomUUID() + ".jsonl");
        Files.writeString(input, rows.stream().map(MAPPER::writeValueAsString).collect(java.util.stream.Collectors.joining("\n")) + "\n");
        return runner(model).run(input, List.of(scene));
    }

    private JudgeRunner runner(JudgeModel model) {
        return new JudgeRunner(model, "FAKE", "fake-judge", CRITERIA);
    }

    private JudgeModel good() { return (system, user) -> response("{\"reasoning\":\"근거 범위 내 선택\",\"violations\":[]}"); }
    private JudgeModel.Response response(String raw) { return new JudgeModel.Response(raw, 12.5, 100, 10); }

    private SeedEvaluationScene scene(boolean ordered, List<String> videos) throws Exception {
        return withChoices(List.of(new SeedEvaluationScene.GoodChoice("PROPOSE", "COURSE",
                List.of("cafe", "meal"), ordered, videos)));
    }

    private SeedEvaluationScene withChoices(List<SeedEvaluationScene.GoodChoice> choices) throws Exception {
        var cafe = new SeedEvaluationScene.Save("eval-seed-cafe", "카페 영상", "영상 요약", "CAFE", "2000-01-01T09:00:00+09:00",
                List.of(new SeedEvaluationScene.PlaceLink("cafe", "장소 소개 맥락")));
        var meal = new SeedEvaluationScene.Save("eval-seed-meal", "식당 영상", "식당 요약", "FOOD", "2000-01-01T09:00:00+09:00",
                List.of(new SeedEvaluationScene.PlaceLink("meal", "식당 맥락")));
        return new SeedEvaluationScene("scene", "tune", "judgment", new SeedEvaluationScene.Seed(Map.of(
                "cafe", new SeedEvaluationScene.Place("카페", "주소", 36.351, 127.38),
                "meal", new SeedEvaluationScene.Place("식당", "주소", 36.351, 127.38335)), List.of(cafe, meal), List.of()),
                new SeedEvaluationScene.Event("cafe", "2026-10-10T18:10:00+09:00"),
                MAPPER.readTree("{\"GOLDEN_SECRET\":true}"), MAPPER.readTree("\"NOTES_SECRET\""), choices);
    }

    private ObjectNode row() {
        return (ObjectNode) MAPPER.readTree("""
                {"runId":"55555555-5555-5555-5555-555555555555","sceneId":"scene","repetition":1,
                 "agentVersion":"test-agent","verdict":{"passed":true,"errors":[]},
                 "input":{"origin":{"placeId":"%s","name":"카페","latitude":36.351,"longitude":127.38,
                     "contents":[{"contentId":"%s","title":"카페 영상","summary":"영상 요약","category":"CAFE",
                        "savedAt":"2000-01-01T00:00:00Z","description":"장소 소개 맥락"}]},
                   "nearbyPersonalCandidates":[{"placeId":"%s","name":"식당","distanceMeters":300,"거리":300,
                     "contents":[{"contentId":"%s","title":"식당 영상","summary":"식당 요약"}]}],
                   "nearbyPublicCandidates":[],"semanticCandidates":[],
                   "situation":{"localDateTime":"2026-10-10T18:10:00","dayOfWeek":"SATURDAY"}},"trace":[],
                 "result":{"result":"PROPOSE","proposalType":"COURSE","proposalPayload":{"items":[
                    {"contentId":"%s","placeId":"%s","source":"PERSONAL"},
                    {"contentId":"%s","placeId":"%s","source":"PERSONAL"}]},
                    "title":"제목","body":"식사 후 카페에 들러보세요","decisionSummary":"저녁 코스"}}
                """.formatted(CAFE, CAFE_VIDEO, MEAL, MEAL_VIDEO, CAFE_VIDEO, CAFE, MEAL_VIDEO, MEAL));
    }
}
