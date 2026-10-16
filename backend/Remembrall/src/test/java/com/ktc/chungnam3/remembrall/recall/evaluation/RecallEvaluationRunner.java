package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class RecallEvaluationRunner {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RecallEvaluationChecker checker = new RecallEvaluationChecker();

    public List<Line> run(List<EvaluationScene> scenes, int repetitions, String provider, String model,
                          EvaluationAgent agent, Path output) throws IOException {
        if (repetitions < 1) {
            throw new IllegalArgumentException("Evaluation repetitions must be positive");
        }
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        var lines = new ArrayList<Line>();
        String agentVersion = agent.version();
        try (var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (var scene : scenes) {
                for (int repetition = 1; repetition <= repetitions; repetition++) {
                    long started = System.nanoTime();
                    LlmRecallAgent.Invocation invocation;
                    String failure = null;
                    try {
                        invocation = agent.invoke(scene.input());
                        if (invocation == null) {
                            throw new IllegalStateException("Missing evaluation invocation");
                        }
                    } catch (LlmRecallAgent.CallException exception) {
                        invocation = exception.invocation();
                        failure = exception.getCause().getClass().getSimpleName();
                    } catch (Exception exception) {
                        invocation = new LlmRecallAgent.Invocation(null, null,
                                (System.nanoTime() - started) / 1_000_000.0, null, null);
                        failure = exception.getClass().getSimpleName();
                    }
                    var line = new Line(scene.id(), repetition, provider, model, agentVersion, invocation.rawResponse(),
                            invocation.result(), checker.check(scene, invocation.result()), invocation.responseTimeMillis(),
                            invocation.inputTokens(), invocation.outputTokens(), failure);
                    writer.write(mapper.writeValueAsString(line));
                    writer.newLine();
                    writer.flush();
                    lines.add(line);
                }
            }
        }
        return List.copyOf(lines);
    }

    public void printSummary(List<Line> lines, PrintStream output) {
        lines.stream().map(line -> line.provider() + "/" + line.model()).distinct().forEach(model -> {
            var runs = lines.stream().filter(line -> (line.provider() + "/" + line.model()).equals(model)).toList();
            long expected = runs.stream().filter(line -> line.checks().expectationMatched()).count();
            long formatted = runs.stream().filter(line -> line.checks().formatPassed()).count();
            output.printf(java.util.Locale.ROOT,
                    "%s runs=%d expected=%.1f%% format=%.1f%% avgLatency=%.1fms avgInputTokens=%s avgOutputTokens=%s%n",
                    model, runs.size(), expected * 100.0 / runs.size(), formatted * 100.0 / runs.size(),
                    average(runs, Line::responseTimeMillis), averageTokens(runs, true), averageTokens(runs, false));
        });
    }

    private double average(List<Line> lines, ToDoubleFunction<Line> value) {
        return lines.stream().mapToDouble(value).average().orElse(0);
    }

    private String averageTokens(List<Line> lines, boolean input) {
        var tokens = lines.stream().map(line -> input ? line.inputTokens() : line.outputTokens())
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average();
        return tokens.isPresent() ? String.format(java.util.Locale.ROOT, "%.1f", tokens.getAsDouble()) : "n/a";
    }

    public interface EvaluationAgent {
        String version();

        LlmRecallAgent.Invocation invoke(RecallAgentInput input) throws Exception;

        static EvaluationAgent from(LlmRecallAgent agent) {
            return new EvaluationAgent() {
                @Override
                public String version() {
                    return agent.version();
                }

                @Override
                public LlmRecallAgent.Invocation invoke(RecallAgentInput input) {
                    return agent.invoke(input);
                }
            };
        }
    }

    public record Line(String sceneId, int repetition, String provider, String model, String agentVersion, String rawResponse,
                       RecallAgentResult result, RecallEvaluationChecker.Checks checks, double responseTimeMillis,
                       Integer inputTokens, Integer outputTokens, String failure) {}
}
