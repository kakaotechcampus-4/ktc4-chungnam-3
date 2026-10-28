package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class JudgeReports {
    private JudgeReports() {}

    static void write(Path report, Path source, Path judged, List<JudgeRunner.Line> lines,
                      ToolUsageMetrics.Summary toolUsage) throws IOException {
        Files.writeString(report, markdown(source, judged, lines, toolUsage), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static String markdown(Path source, Path judged, List<JudgeRunner.Line> lines, ToolUsageMetrics.Summary toolUsage) {
        var out = new StringBuilder("# Judge 결과\n\n");
        out.append("입력: `").append(source).append("`\n\n결과: `").append(judged).append("`\n\n");
        out.append("Judge: ").append(lines.stream().map(line -> line.judgeProvider() + "/" + line.judgeModel())
                .distinct().collect(Collectors.joining(", "))).append("\n\n");
        lines.stream().filter(line -> line.criteriaPath() != null).findFirst().ifPresent(line ->
                out.append("기준 문서: `").append(line.criteriaPath()).append("`\n\nSHA-256: `")
                        .append(line.criteriaSha256()).append("`\n\n"));
        out.append("에이전트를 다시 실행하거나 임베딩/DB를 호출하지 않고 기존 결과만 채점했다.\n\n");
        out.append("## 품질 점수\n\n");
        tableHeader(out);
        aggregate(out, "전체", lines);
        out.append("\nAGENT_FAILED, JUDGE_FAILED와 UNCOMPARABLE은 점수가 없으며 평균 점수의 분모에서 제외한다. 필수 조건 위반이 확인된 BAD는 선택의 대응 가능 여부와 관계없이 유지한다.\n\n");
        reliability(out, lines);
        out.append(toolUsage.markdown());
        groups(out, "split", lines, line -> unknown(line.split()));
        groups(out, "kind", lines, line -> unknown(line.kind()));
        out.append("## 장면별 라벨\n\n| 장면 | 반복별 라벨 | 전부 동일 | 평균 | 선택 요약 |\n|---|---|---|---:|---|\n");
        var grouped = lines.stream().collect(Collectors.groupingBy(JudgeRunner.Line::sceneId,
                LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((id, rows) -> out.append("| ").append(cell(id)).append(" | ")
                .append(rows.stream().map(row -> row.repetition() + ": " + row.label()).collect(Collectors.joining(", ")))
                .append(" | ").append(rows.stream().map(JudgeRunner.Line::label).distinct().count() == 1 ? "예" : "아니오")
                .append(" | ").append(average(rows)).append(" | ")
                .append(rows.stream().map(row -> row.comparison() == null ? row.agentResultSummary()
                        : row.comparison().selection().toString()).map(JudgeReports::cell)
                        .distinct().collect(Collectors.joining("; "))).append(" |\n"));
        out.append("\n## 위반 조건별 건수\n\n| 조건 | 건수 |\n|---|---:|\n");
        for (int condition = 1; condition <= 7; condition++) {
            int number = condition;
            long count = lines.stream().filter(line -> line.violations().stream()
                    .anyMatch(violation -> violation.conditionNumber() == number)).count();
            out.append("| ").append(condition).append(" | ").append(count).append(" |\n");
        }
        out.append("\n## 진단\n\n");
        out.append("- 침묵만 goodChoices인데 제안: ").append(lines.stream().filter(JudgeRunner.Line::silenceExpectedButProposed).count()).append("\n");
        out.append("- 제안만 goodChoices인데 침묵: ").append(lines.stream().filter(JudgeRunner.Line::proposalExpectedButSilent).count()).append("\n");
        out.append("- AGENT_FAILED: ").append(lines.stream().filter(line -> line.label() == JudgeRunner.Label.AGENT_FAILED).count()).append("\n");
        out.append("- JUDGE_FAILED: ").append(lines.stream().filter(line -> line.label() == JudgeRunner.Label.JUDGE_FAILED).count()).append("\n");
        out.append("- UNCOMPARABLE 라벨: ").append(lines.stream().filter(line -> line.label() == JudgeRunner.Label.UNCOMPARABLE).count()).append("\n");
        out.append("- 비교 불가: ").append(lines.stream().filter(line -> line.comparison() != null
                && "UNCOMPARABLE".equals(line.comparison().status())).count()).append("\n");
        out.append("- 평균 Judge 호출: ").append(String.format(Locale.ROOT, "%.1fms", lines.stream()
                .filter(line -> line.judgeTimeMillis() != null && line.judgeTimeMillis() > 0)
                .mapToDouble(JudgeRunner.Line::judgeTimeMillis).average().orElse(0))).append("\n");
        out.append("- Judge 입력/출력 토큰 합계: ").append(lines.stream().map(JudgeRunner.Line::inputTokens)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum()).append(" / ")
                .append(lines.stream().map(JudgeRunner.Line::outputTokens).filter(Objects::nonNull)
                        .mapToLong(Integer::longValue).sum()).append("\n\n");
        out.append("## 비교 불가 상세\n\n| 장면 | 반복 | 라벨 | 대응 실패 ID 및 사유 |\n|---|---:|---|---|\n");
        var uncomparable = lines.stream().filter(line -> line.comparison() != null
                && "UNCOMPARABLE".equals(line.comparison().status())).toList();
        if (uncomparable.isEmpty()) {
            out.append("| 해당 없음 | — | — | — |\n");
        } else {
            for (var line : uncomparable) {
                out.append("| ").append(cell(line.sceneId())).append(" | ").append(line.repetition())
                        .append(" | ").append(line.label()).append(" | ")
                        .append(cell(String.join("; ", line.comparison().errors()))).append(" |\n");
            }
        }
        out.append('\n');
        out.append("## 판정 근거\n\n| 장면 | 반복 | 라벨 | 위반 및 오류 | Judge reasoning |\n|---|---:|---|---|---|\n");
        for (var line : lines) {
            out.append("| ").append(cell(line.sceneId())).append(" | ").append(line.repetition()).append(" | ")
                    .append(line.label()).append(" | ").append(cell(line.violations().stream()
                            .map(violation -> violation.conditionNumber() + ": " + violation.evidence())
                            .collect(Collectors.joining("; "))))
                    .append(line.failure() == null ? "" : cell(line.failure().toString())).append(" | ")
                    .append(cell(line.reasoning())).append(" |\n");
        }
        out.append("\n사람 라벨을 채운 CSV로 recallJudge의 recall.judge.human-labels 모드를 실행하면 일치율과 교차표를 얻을 수 있다.\n");
        return out.toString();
    }

    private static void groups(StringBuilder out, String heading, List<JudgeRunner.Line> lines,
                               Function<JudgeRunner.Line, String> key) {
        out.append("## ").append(heading).append("별\n\n");
        tableHeader(out);
        lines.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()))
                .forEach((name, group) -> aggregate(out, name, group));
        out.append('\n');
    }

    private static void tableHeader(StringBuilder out) {
        out.append("| 구분 | 전체 | 채점됨 | 평균 | GOOD | ACCEPTABLE | BAD | AGENT_FAILED | JUDGE_FAILED | UNCOMPARABLE |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    }

    private static void reliability(StringBuilder out, List<JudgeRunner.Line> lines) {
        out.append("## 신뢰성\n\n");
        out.append("전체 실행 수: ").append(lines.size()).append("\n\n");
        for (var label : List.of(JudgeRunner.Label.AGENT_FAILED, JudgeRunner.Label.JUDGE_FAILED)) {
            long count = lines.stream().filter(line -> line.label() == label).count();
            String rate = lines.isEmpty() ? "N/A" : String.format(Locale.ROOT, "%.1f%%", 100.0 * count / lines.size());
            out.append("- ").append(label).append(": ").append(count).append(" / ").append(lines.size())
                    .append(" (").append(rate).append(")\n");
        }
        var failures = lines.stream().filter(line -> line.label() == JudgeRunner.Label.AGENT_FAILED
                || line.label() == JudgeRunner.Label.JUDGE_FAILED).toList();
        out.append("\n### 실패 단계별 건수\n\n| 구분 | 실패 단계 | 건수 |\n|---|---|---:|\n");
        if (failures.isEmpty()) {
            out.append("| 해당 없음 | — | 0 |\n");
        } else {
            failures.stream().collect(Collectors.groupingBy(line -> line.label() + " | "
                            + cell(line.failure() == null ? null : line.failure().stage()),
                    LinkedHashMap::new, Collectors.counting()))
                    .forEach((stage, count) -> out.append("| ").append(stage).append(" | ").append(count).append(" |\n"));
        }
        out.append("\n### 장면별 실패 발생 위치\n\n| 구분 | 장면 | 반복 | 원본 줄 | 실패 단계 | 오류 메시지 |\n|---|---|---:|---:|---|---|\n");
        if (failures.isEmpty()) {
            out.append("| 해당 없음 | — | — | — | — | — |\n");
        } else {
            for (var line : failures) {
                out.append("| ").append(line.label()).append(" | ").append(cell(line.sceneId())).append(" | ")
                        .append(line.repetition()).append(" | ").append(line.sourceLine()).append(" | ")
                        .append(cell(line.failure() == null ? null : line.failure().stage())).append(" | ")
                        .append(cell(line.failure() == null ? null : line.failure().message())).append(" |\n");
            }
        }
        out.append('\n');
    }

    private static void aggregate(StringBuilder out, String name, List<JudgeRunner.Line> lines) {
        out.append("| ").append(cell(name)).append(" | ").append(lines.size()).append(" | ")
                .append(lines.stream().filter(line -> line.score() != null).count()).append(" | ").append(average(lines));
        Arrays.stream(JudgeRunner.Label.values()).forEach(label -> out.append(" | ")
                .append(lines.stream().filter(line -> line.label() == label).count()));
        out.append(" |\n");
    }

    private static String average(List<JudgeRunner.Line> lines) {
        var average = lines.stream().map(JudgeRunner.Line::score).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average();
        return average.isPresent() ? String.format(Locale.ROOT, "%.3f", average.getAsDouble()) : "N/A";
    }

    private static String unknown(String value) { return value == null ? "미지정" : value; }
    private static String cell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
