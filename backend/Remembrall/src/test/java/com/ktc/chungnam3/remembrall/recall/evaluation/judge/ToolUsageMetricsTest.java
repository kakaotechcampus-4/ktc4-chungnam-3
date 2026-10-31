package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.ktc.chungnam3.remembrall.recall.evaluation.SeedEvaluationScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolUsageMetricsTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    @TempDir Path directory;

    @Test
    void countsEligibleExecutionsIncludingFailuresWithoutCountingDuplicateCallsOrModelCalls() {
        var scenes = List.of(scene("check-search", "tool", "checkVisitConditions", "searchCandidates"),
                scene("search", "tool", "searchCandidates"), scene("quiet", "no_tool"));
        var rows = List.of(row("""
                {"sceneId":"check-search","trace":[
                  {"step":"TOOL_CALL","name":"checkVisitConditions","success":false},
                  {"step":"TOOL_CALL","name":"checkVisitConditions"},
                  {"step":"TOOL_CALL","name":"searchCandidates"}]}
                """), row("""
                {"sceneId":"check-search","failure":{"stage":"MODEL_CALL"},"trace":[]}
                """), row("""
                {"sceneId":"search","trace":[]}
                """), row("""
                {"sceneId":"quiet","trace":[{"step":"MODEL_CALL","name":"checkVisitConditions"}]}
                """), row("""
                {"sceneId":"quiet","trace":[{"step":"TOOL_CALL","name":"searchCandidates"}]}
                """));
        var summary = ToolUsageMetrics.summarize(scenes, rows);
        assertThat(summary.check()).isEqualTo(new ToolUsageMetrics.Rate(1, 2));
        assertThat(summary.search()).isEqualTo(new ToolUsageMetrics.Rate(1, 3));
        assertThat(summary.unnecessary()).isEqualTo(new ToolUsageMetrics.Rate(1, 2));
        assertThat(summary.markdown()).contains("| 확인 호출률 | 1 | 2 | 50.0% |",
                "| 검색 호출률 | 1 | 3 | 33.3% |", "| 불필요 호출률 | 1 | 2 | 50.0% |");
    }

    @Test
    void legacyScenesWithNoEligibleRunsDoNotInventRates() {
        var summary = ToolUsageMetrics.summarize(List.of(scene("old", "judgment")), List.of(row("{\"sceneId\":\"old\"}")));
        assertThat(summary.check().percent()).isEqualTo("N/A");
        assertThat(summary.search().percent()).isEqualTo("N/A");
        assertThat(summary.unnecessary().percent()).isEqualTo("N/A");
        assertThat(summary.markdown()).isEmpty();
    }

    @Test
    void sourceReadingAllowsBomAndDoesNotTurnMalformedOrUnknownRowsIntoEligibleRuns() throws Exception {
        Path source = directory.resolve("source.jsonl");
        Files.writeString(source, "\uFEFF{\"sceneId\":\"quiet\",\"trace\":[]}\nnot json\n[]\n{\"sceneId\":\"missing\"}\n");
        var summary = ToolUsageMetrics.read(source, List.of(scene("quiet", "no_tool")));
        assertThat(summary.unnecessary()).isEqualTo(new ToolUsageMetrics.Rate(0, 1));
        assertThat(summary.scenes()).hasSize(1);
    }

    private SeedEvaluationScene scene(String id, String kind, String... tools) {
        return new SeedEvaluationScene(id, "tune", kind, null, null, null, null, List.of(), null, List.of(), List.of(tools));
    }

    private JsonNode row(String json) { return mapper.readTree(json); }
}
