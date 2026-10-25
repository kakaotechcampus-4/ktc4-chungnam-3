package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class HumanLabelCsv {
    private HumanLabelCsv() {}

    static void write(Path path, List<JudgeRunner.Line> lines) throws IOException {
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("rowId", "sceneId", "repetition", "agentResultSummary", "codeComparison",
                "judgeViolations", "finalLabel", "humanLabel"));
        for (var line : lines) {
            rows.add(List.of(line.rowId(), line.sceneId(), String.valueOf(line.repetition()),
                    line.agentResultSummary() == null ? "" : line.agentResultSummary(),
                    line.comparison() == null ? "NOT_RUN" : line.comparison().toString(),
                    line.violations().stream().map(violation -> violation.conditionNumber() + ": " + violation.evidence())
                            .collect(Collectors.joining("; ")), line.label().name(), ""));
        }
        String csv = "\uFEFF" + rows.stream().map(row -> row.stream().map(HumanLabelCsv::quote)
                .collect(Collectors.joining(","))).collect(Collectors.joining("\r\n", "", "\r\n"));
        Files.writeString(path, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static Agreement compare(Path judgedJsonl, Path humanCsv) throws IOException {
        var expected = new HashMap<String, JudgeRunner.Line>();
        for (String raw : Files.readAllLines(judgedJsonl, StandardCharsets.UTF_8)) {
            if (raw.isBlank()) { continue; }
            var line = JudgeRunner.MAPPER.readValue(raw, JudgeRunner.Line.class);
            if (expected.putIfAbsent(line.rowId(), line) != null) {
                throw new IllegalArgumentException("Duplicate judged rowId: " + line.rowId());
            }
        }
        var csv = parse(Files.readString(humanCsv, StandardCharsets.UTF_8));
        if (csv.isEmpty()) { throw new IllegalArgumentException("Empty human-label CSV"); }
        var headers = csv.getFirst();
        int rowIdAt = headers.indexOf("rowId");
        int humanAt = headers.indexOf("humanLabel");
        if (rowIdAt < 0 || humanAt < 0) { throw new IllegalArgumentException("CSV requires rowId and humanLabel columns"); }
        int compared = 0;
        int agreed = 0;
        int blank = 0;
        int agentFailed = 0;
        int unscored = 0;
        int uncomparable = 0;
        var seen = new HashSet<String>();
        var matrix = new LinkedHashMap<String, Integer>();
        for (int index = 1; index < csv.size(); index++) {
            var cells = csv.get(index);
            if (cells.size() != headers.size()) { throw new IllegalArgumentException("Invalid CSV column count at row " + (index + 1)); }
            String rowId = cells.get(rowIdAt);
            var line = expected.get(rowId);
            if (line == null || !seen.add(rowId)) { throw new IllegalArgumentException("Unknown or duplicate CSV rowId: " + rowId); }
            String human = cells.get(humanAt).strip().toUpperCase(Locale.ROOT);
            if (human.isBlank()) { blank++; continue; }
            if (!Set.of("GOOD", "ACCEPTABLE", "BAD").contains(human)) {
                throw new IllegalArgumentException("Invalid human label at row " + (index + 1) + ": " + human);
            }
            if (line.label() == JudgeRunner.Label.AGENT_FAILED) { agentFailed++; continue; }
            if (line.label() == JudgeRunner.Label.JUDGE_FAILED) { unscored++; continue; }
            if (line.label() == JudgeRunner.Label.UNCOMPARABLE) { uncomparable++; continue; }
            compared++;
            if (line.label().name().equals(human)) { agreed++; }
            matrix.merge(line.label() + " -> " + human, 1, Integer::sum);
        }
        return new Agreement(compared, agreed, compared == 0 ? null : (double) agreed / compared,
                blank, agentFailed, unscored, uncomparable, expected.size() - seen.size(), Map.copyOf(matrix));
    }

    static List<List<String>> parse(String source) {
        String csv = source.startsWith("\uFEFF") ? source.substring(1) : source;
        var rows = new ArrayList<List<String>>();
        var cells = new ArrayList<String>();
        var value = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int index = 0; index < csv.length(); index++) {
            char ch = csv.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') { value.append('"'); index++; }
                    else { quoted = false; closed = true; }
                } else { value.append(ch); }
            } else if (ch == '"') {
                if (!value.isEmpty() || closed) { throw new IllegalArgumentException("Invalid CSV quote"); }
                quoted = true;
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                cells.add(value.toString());
                value.setLength(0);
                closed = false;
                if (ch != ',') {
                    if (ch == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') { index++; }
                    if (cells.size() > 1 || !cells.getFirst().isBlank()) { rows.add(List.copyOf(cells)); }
                    cells.clear();
                }
            } else {
                if (closed) { throw new IllegalArgumentException("Unexpected characters after CSV quote"); }
                value.append(ch);
            }
        }
        if (quoted) { throw new IllegalArgumentException("Unclosed CSV quote"); }
        if (!value.isEmpty() || !cells.isEmpty() || closed) {
            cells.add(value.toString()); rows.add(List.copyOf(cells));
        }
        return List.copyOf(rows);
    }

    private static String quote(String value) {
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) { value = "'" + value; }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public record Agreement(int compared, int agreed, Double agreementRate, int blankHumanLabels,
                            int agentFailedSkipped, int judgeFailedSkipped, int uncomparableSkipped, int missingRows,
                            Map<String, Integer> combinations) {
        public String markdown() {
            var out = new StringBuilder("# 사람 라벨 비교\n\n");
            out.append("비교 ").append(compared).append("건 / 일치 ").append(agreed).append("건 / 일치율 ")
                    .append(agreementRate == null ? "N/A" : String.format(Locale.ROOT, "%.1f%%", agreementRate * 100))
                    .append("\n\n미입력 ").append(blankHumanLabels).append(" / CSV 누락 ").append(missingRows)
                    .append(" / AGENT_FAILED 제외 ").append(agentFailedSkipped)
                    .append(" / JUDGE_FAILED 제외 ").append(judgeFailedSkipped)
                    .append(" / UNCOMPARABLE 제외 ").append(uncomparableSkipped).append("\n\n");
            out.append("| Judge \\ 사람 | GOOD | ACCEPTABLE | BAD |\n|---|---:|---:|---:|\n");
            for (String judge : List.of("GOOD", "ACCEPTABLE", "BAD")) {
                out.append("| ").append(judge);
                for (String human : List.of("GOOD", "ACCEPTABLE", "BAD")) {
                    out.append(" | ").append(combinations.getOrDefault(judge + " -> " + human, 0));
                }
                out.append(" |\n");
            }
            return out.toString();
        }
    }
}
