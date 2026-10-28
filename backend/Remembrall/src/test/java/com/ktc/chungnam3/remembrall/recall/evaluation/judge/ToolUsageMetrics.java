package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.ktc.chungnam3.remembrall.recall.evaluation.SeedEvaluationScene;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class ToolUsageMetrics {
    private ToolUsageMetrics() {}

    static Summary read(Path source, List<SeedEvaluationScene> scenes) throws IOException {
        var rows = new ArrayList<JsonNode>();
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
                if (raw.isBlank()) continue;
                try {
                    var row = JudgeRunner.MAPPER.readTree(raw);
                    if (row.isObject()) rows.add(row);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return summarize(scenes, rows);
    }

    static Summary summarize(List<SeedEvaluationScene> scenes, List<? extends JsonNode> rows) {
        var byId = new LinkedHashMap<String, MutableUsage>();
        scenes.forEach(scene -> byId.put(scene.id(), new MutableUsage(scene)));
        for (var row : rows) {
            var usage = byId.get(row.path("sceneId").asText());
            if (usage == null) continue;
            var calls = new HashSet<String>();
            for (var entry : row.path("trace")) {
                if ("TOOL_CALL".equals(entry.path("step").asText())) calls.add(entry.path("name").asText());
            }
            usage.runs++;
            if (calls.contains("checkVisitConditions")) usage.checked++;
            if (calls.contains("searchCandidates")) usage.searched++;
            if (!calls.isEmpty()) usage.used++;
        }
        var usages = byId.values().stream().filter(usage -> usage.runs > 0).map(usage ->
                new SceneUsage(usage.scene.id(), usage.scene.kind(), usage.scene.expectedTools(), usage.runs,
                        usage.checked, usage.searched, usage.used)).toList();
        var check = usages.stream().filter(usage -> usage.expectedTools().contains("checkVisitConditions")).toList();
        var search = usages.stream().filter(usage -> usage.expectedTools().contains("searchCandidates")).toList();
        var unnecessary = usages.stream().filter(usage -> "no_tool".equals(usage.kind())).toList();
        return new Summary(new Rate(check.stream().mapToInt(SceneUsage::checked).sum(), check.stream().mapToInt(SceneUsage::runs).sum()),
                new Rate(search.stream().mapToInt(SceneUsage::searched).sum(), search.stream().mapToInt(SceneUsage::runs).sum()),
                new Rate(unnecessary.stream().mapToInt(SceneUsage::used).sum(), unnecessary.stream().mapToInt(SceneUsage::runs).sum()), usages);
    }

    record Rate(int called, int eligible) {
        String percent() { return eligible == 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", called * 100.0 / eligible); }
    }

    record SceneUsage(String sceneId, String kind, List<String> expectedTools, int runs, int checked, int searched, int used) {}

    record Summary(Rate check, Rate search, Rate unnecessary, List<SceneUsage> scenes) {
        String markdown() {
            if (check.eligible() == 0 && search.eligible() == 0 && unnecessary.eligible() == 0) return "";
            var out = new StringBuilder("## 도구 호출 지표\n\n실패한 실행도 분모에 포함하며, 실제 TOOL_CALL Trace가 있는 실행을 센다. 같은 실행의 반복 호출은 한 번만 센다.\n\n");
            out.append("| 지표 | 호출한 실행 | 대상 실행 | 비율 |\n|---|---:|---:|---:|\n");
            append(out, "확인 호출률", check);
            append(out, "검색 호출률", search);
            append(out, "불필요 호출률", unnecessary);
            out.append("\n확인·검색의 대상은 expectedTools에 해당 도구가 있는 실행이고, 불필요 호출의 대상은 kind=no_tool인 실행이다.\n\n");
            out.append("| 장면 | kind | expectedTools | 실행 | 확인 호출 실행 | 검색 호출 실행 | 도구 사용 실행 |\n|---|---|---|---:|---:|---:|---:|\n");
            for (var scene : scenes) {
                out.append("| ").append(scene.sceneId()).append(" | ").append(scene.kind()).append(" | ")
                        .append(String.join(", ", scene.expectedTools())).append(" | ").append(scene.runs())
                        .append(" | ").append(scene.checked()).append(" | ").append(scene.searched()).append(" | ")
                        .append(scene.used()).append(" |\n");
            }
            return out.append('\n').toString();
        }

        private static void append(StringBuilder out, String label, Rate rate) {
            out.append("| ").append(label).append(" | ").append(rate.called()).append(" | ")
                    .append(rate.eligible()).append(" | ").append(rate.percent()).append(" |\n");
        }
    }

    private static final class MutableUsage {
        private final SeedEvaluationScene scene;
        private int runs;
        private int checked;
        private int searched;
        private int used;

        private MutableUsage(SeedEvaluationScene scene) { this.scene = scene; }
    }
}
