package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumanLabelCsvTest {
    @TempDir Path directory;

    @Test
    void agreementExcludesBlankAndJudgeFailuresAndBuildsConfusionMatrix() throws Exception {
        var lines = List.of(line(1, "GOOD"), line(2, "ACCEPTABLE"), line(3, "BAD"),
                line(4, "JUDGE_FAILED"), line(5, "GOOD"));
        Path judged = judged(lines);
        Path csv = directory.resolve("human.csv");
        HumanLabelCsv.write(csv, lines);
        var rows = editable(Files.readString(csv));
        for (int index = 1; index <= 4; index++) {
            rows.get(index).set(7, List.of("GOOD", "BAD", "BAD", "GOOD").get(index - 1));
        }
        Files.writeString(csv, encode(rows));
        var agreement = HumanLabelCsv.compare(judged, csv);
        assertThat(agreement.compared()).isEqualTo(3);
        assertThat(agreement.agreed()).isEqualTo(2);
        assertThat(agreement.agreementRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(agreement.blankHumanLabels()).isEqualTo(1);
        assertThat(agreement.judgeFailedSkipped()).isEqualTo(1);
        assertThat(agreement.missingRows()).isZero();
        assertThat(agreement.combinations()).containsEntry("ACCEPTABLE -> BAD", 1);
        assertThat(agreement.markdown()).contains("66.7%", "| ACCEPTABLE | 0 | 0 | 1 |");
    }

    @Test
    void csvHandlesBomCommasQuotesAndMultilineFields() throws Exception {
        var row = (ObjectNode) JudgeRunner.MAPPER.valueToTree(line(1, "GOOD"));
        row.put("agentResultSummary", "이름, \"인용\"\n두 번째 줄");
        var line = JudgeRunner.MAPPER.treeToValue(row, JudgeRunner.Line.class);
        Path csv = directory.resolve("quoted.csv");
        HumanLabelCsv.write(csv, List.of(line));
        var rows = HumanLabelCsv.parse(Files.readString(csv));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(3)).isEqualTo("이름, \"인용\"\n두 번째 줄");
        assertThat(rows.getFirst()).contains("humanLabel");
    }

    @Test
    void humanComparisonUsesOriginalJudgedLabelNotEditableCsvLabel() throws Exception {
        var lines = List.of(line(1, "GOOD"));
        Path judged = judged(lines);
        Path csv = directory.resolve("edited.csv");
        HumanLabelCsv.write(csv, lines);
        var rows = editable(Files.readString(csv));
        rows.get(1).set(6, "BAD");
        rows.get(1).set(7, "GOOD");
        Files.writeString(csv, encode(rows));
        assertThat(HumanLabelCsv.compare(judged, csv).agreementRate()).isEqualTo(1.0);
    }

    @Test
    void invalidHumanLabelsAndDuplicateRowsAreRejected() throws Exception {
        var lines = List.of(line(1, "GOOD"));
        Path judged = judged(lines);
        Path csv = directory.resolve("invalid.csv");
        HumanLabelCsv.write(csv, lines);
        var rows = editable(Files.readString(csv));
        rows.get(1).set(7, "MAYBE");
        Files.writeString(csv, encode(rows));
        assertThatThrownBy(() -> HumanLabelCsv.compare(judged, csv)).hasMessageContaining("Invalid human label");
        rows.get(1).set(7, "GOOD");
        rows.add(new ArrayList<>(rows.get(1)));
        Files.writeString(csv, encode(rows));
        assertThatThrownBy(() -> HumanLabelCsv.compare(judged, csv)).hasMessageContaining("duplicate CSV rowId");
    }

    @Test
    void noHumanLabelsReportsNoRateAndMissingRowsAreCounted() throws Exception {
        var lines = List.of(line(1, "GOOD"), line(2, "BAD"));
        Path judged = judged(lines);
        Path csv = directory.resolve("empty-labels.csv");
        HumanLabelCsv.write(csv, List.of(lines.getFirst()));
        var agreement = HumanLabelCsv.compare(judged, csv);
        assertThat(agreement.agreementRate()).isNull();
        assertThat(agreement.missingRows()).isEqualTo(1);
        assertThat(agreement.markdown()).contains("일치율 N/A");
    }

    @Test
    void uncomparableLabelIsExcludedFromHumanAgreement() throws Exception {
        var lines = List.of(line(1, "GOOD"), line(2, "UNCOMPARABLE"));
        Path judged = judged(lines);
        Path csv = directory.resolve("uncomparable.csv");
        HumanLabelCsv.write(csv, lines);
        var rows = editable(Files.readString(csv));
        rows.get(1).set(7, "GOOD");
        rows.get(2).set(7, "BAD");
        Files.writeString(csv, encode(rows));
        var agreement = HumanLabelCsv.compare(judged, csv);
        assertThat(agreement.compared()).isEqualTo(1);
        assertThat(agreement.agreementRate()).isEqualTo(1.0);
        assertThat(agreement.uncomparableSkipped()).isEqualTo(1);
        assertThat(agreement.markdown()).contains("UNCOMPARABLE 제외 1");
    }

    @Test
    void agentAndJudgeFailuresKeepTheirCsvLabelsAndAreExcludedSeparatelyFromAgreement() throws Exception {
        var lines = List.of(line(1, "GOOD"), line(2, "AGENT_FAILED"), line(3, "JUDGE_FAILED"));
        Path judged = judged(lines);
        Path csv = directory.resolve("failures.csv");
        HumanLabelCsv.write(csv, lines);
        var rows = editable(Files.readString(csv));
        assertThat(rows.get(2).get(6)).isEqualTo("AGENT_FAILED");
        assertThat(rows.get(3).get(6)).isEqualTo("JUDGE_FAILED");
        for (int index = 1; index < rows.size(); index++) {
            rows.get(index).set(7, "GOOD");
        }
        Files.writeString(csv, encode(rows));

        var agreement = HumanLabelCsv.compare(judged, csv);

        assertThat(agreement.compared()).isEqualTo(1);
        assertThat(agreement.agreed()).isEqualTo(1);
        assertThat(agreement.agreementRate()).isEqualTo(1.0);
        assertThat(agreement.agentFailedSkipped()).isEqualTo(1);
        assertThat(agreement.judgeFailedSkipped()).isEqualTo(1);
        assertThat(agreement.markdown()).contains("AGENT_FAILED 제외 1", "JUDGE_FAILED 제외 1");
    }

    @Test
    void malformedCsvIsNotSilentlyAccepted() {
        assertThatThrownBy(() -> HumanLabelCsv.parse("\"unclosed")).hasMessageContaining("Unclosed CSV quote");
        assertThatThrownBy(() -> HumanLabelCsv.parse("\"closed\"tail")).hasMessageContaining("Unexpected characters");
    }

    private JudgeRunner.Line line(int number, String label) {
        var row = JudgeRunner.MAPPER.createObjectNode();
        row.put("judgeRunId", UUID.randomUUID().toString()).put("sourceRunId", "source-run")
                .put("sourceLine", number).put("sceneId", "scene").put("repetition", number)
                .put("silenceExpectedButProposed", false).put("proposalExpectedButSilent", false)
                .put("label", label);
        if (label.equals("AGENT_FAILED") || label.equals("JUDGE_FAILED") || label.equals("UNCOMPARABLE")) {
            row.putNull("score");
        } else {
            row.put("score", label.equals("GOOD") ? 5 : label.equals("ACCEPTABLE") ? 3 : 1);
        }
        row.putArray("violations");
        return JudgeRunner.MAPPER.treeToValue(row, JudgeRunner.Line.class);
    }

    private Path judged(List<JudgeRunner.Line> lines) throws Exception {
        Path path = directory.resolve("judged.jsonl");
        Files.writeString(path, lines.stream().map(JudgeRunner.MAPPER::writeValueAsString)
                .collect(Collectors.joining("\n")) + "\n");
        return path;
    }

    private List<List<String>> editable(String csv) {
        return HumanLabelCsv.parse(csv).stream().map(row -> (List<String>) new ArrayList<>(row))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String encode(List<List<String>> rows) {
        return rows.stream().map(row -> row.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","))).collect(Collectors.joining("\r\n"));
    }
}
