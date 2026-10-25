package com.ktc.chungnam3.remembrall.recall.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecallEvaluationMainTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    @TempDir Path directory;

    @Test
    void tuneSelectionKeepsAllTwentyFourScenesIncludingThreeToolScenes() throws Exception {
        var scenes = load("datasets/golden-scenes.json");
        var selected = RecallEvaluationMain.selectSeedScenes(scenes, "tune");
        assertThat(scenes).hasSize(30);
        assertThat(selected).hasSize(24).allSatisfy(scene -> {
            assertThat(scene.split()).isEqualTo("tune");
            assertThat(scene.reference()).isNotNull();
            assertThat(scene.judgeNotes()).isNotNull();
            assertThat(scene.seed().personalSaves()).allSatisfy(save ->
                    assertThat(save.videoId()).startsWith("eval-seed-gold-"));
        });
        assertThat(Arrays.stream(selected).filter(scene -> "tool".equals(scene.kind())).count()).isEqualTo(3);
        assertThat(selected).extracting(SeedEvaluationScene::id).noneMatch(id -> id.startsWith("h"));
    }

    @Test
    void absentSplitPreservesLegacyScenesAndUnfilteredDatasets() throws Exception {
        var scenes = load("datasets/golden-scenes.json");
        assertThat(RecallEvaluationMain.selectSeedScenes(scenes, null)).isSameAs(scenes);
        assertThat(RecallEvaluationMain.selectSeedScenes(scenes, "")).isSameAs(scenes);
        assertThat(load("seed-scenes.json")).hasSize(4).allSatisfy(scene -> {
            assertThat(scene.split()).isNull();
            assertThat(scene.kind()).isNull();
        });
    }

    @Test
    void toolDatasetLoadsAllEighteenScenesWithExpectedToolsAndTopLevelConditions() throws Exception {
        var scenes = load("datasets/tool-scenes.json");
        assertThat(scenes).hasSize(18);
        assertThat(Arrays.stream(scenes).filter(scene -> "tool".equals(scene.kind())).count()).isEqualTo(15);
        assertThat(Arrays.stream(scenes).filter(scene -> "no_tool".equals(scene.kind())).count()).isEqualTo(3);
        assertThat(scenes[0].expectedTools()).containsExactly("checkVisitConditions");
        assertThat(scenes[1].weather().precipitation().name()).isEqualTo("RAIN");
        assertThat(scenes[9].recentNotifications()).hasSize(1);
        assertThat(scenes[9].recentNotifications().getFirst().opened()).isFalse();
        assertThat(scenes[12].expectedTools()).containsExactly("searchCandidates", "checkVisitConditions");
        assertThat(scenes[15].expectedTools()).isEmpty();
    }

    @Test
    void unknownSplitFailsBeforeCallingAgents() throws Exception {
        var scenes = load("datasets/golden-scenes.json");
        assertThatThrownBy(() -> RecallEvaluationMain.selectSeedScenes(scenes, "unknown"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No evaluation scenes for split");
    }

    @Test
    void failureRecordsKeepSplitAndKindWithoutGoldenAnnotations() throws Exception {
        var scene = Arrays.stream(load("datasets/golden-scenes.json"))
                .filter(value -> "tool".equals(value.kind())).findFirst().orElseThrow();
        Path output;
        try (var run = EvaluationRun.start(directory.resolve("failure.jsonl"))) {
            output = run.output();
            var lines = SeedEvaluationRunner.recordFailure(List.of(scene), 1, "FAKE", "test-model",
                    SeedEvaluationRunner.Stage.SEED, new IllegalStateException("seed unavailable"), run);
            assertThat(lines.getFirst().split()).isEqualTo("tune");
            assertThat(lines.getFirst().kind()).isEqualTo("tool");
        }
        var row = mapper.readTree(Files.readAllLines(output).getFirst());
        assertThat(row.get("split").asText()).isEqualTo("tune");
        assertThat(row.get("kind").asText()).isEqualTo("tool");
        assertThat(row.has("reference")).isFalse();
        assertThat(row.has("judgeNotes")).isFalse();
        assertThat(row.at("/failure/stage").asText()).isEqualTo("SEED");
    }

    private SeedEvaluationScene[] load(String resource) throws Exception {
        try (var stream = new ClassPathResource("recall/evaluation/" + resource).getInputStream()) {
            return mapper.readValue(stream, SeedEvaluationScene[].class);
        }
    }
}
