package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.service.ProposalValidator;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public class SeedEvaluationRunner {
    private final SeedEvaluationSeeder seeder;
    private final RecallAgentInputService inputs;
    private final ProposalValidator validator;
    private final RecallTraceService traces;

    public SeedEvaluationRunner(SeedEvaluationSeeder seeder, RecallAgentInputService inputs,
                                ProposalValidator validator, RecallTraceService traces) {
        this.seeder = seeder;
        this.inputs = inputs;
        this.validator = validator;
        this.traces = traces;
    }

    public List<Line> run(List<SeedEvaluationScene> scenes, int repetitions, String provider, String model,
                          RecallEvaluationRunner.EvaluationAgent agent, Path output) throws IOException {
        if (repetitions < 1) {
            throw new IllegalArgumentException("Evaluation repetitions must be positive");
        }
        try (var run = EvaluationRun.start(output)) {
            return run(scenes, repetitions, provider, model, agent, run);
        }
    }

    public List<Line> run(List<SeedEvaluationScene> scenes, int repetitions, String provider, String model,
                          RecallEvaluationRunner.EvaluationAgent agent, EvaluationRun run) throws IOException {
        if (repetitions < 1) {
            throw new IllegalArgumentException("Evaluation repetitions must be positive");
        }
        var lines = new ArrayList<Line>();
        String version = null;
        Exception versionFailure = null;
        try {
            version = agent.version();
        } catch (Exception exception) {
            versionFailure = exception;
        }
        for (var scene : scenes) {
            long started = System.nanoTime();
            SeedEvaluationSeeder.SeededScene seeded = null;
            Failure seedFailure = null;
            try {
                seeded = seeder.resetAndSeed(scene);
            } catch (Exception exception) {
                seedFailure = failure(Stage.SEED, exception);
            }
            double seedMillis = elapsed(started);
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                Line line;
                if (seedFailure != null) {
                    line = failed(run, scene, repetition, provider, model, version, seedMillis, seedFailure);
                } else if (versionFailure != null) {
                    line = failed(run, scene, repetition, provider, model, version, seedMillis,
                            failure(Stage.MODEL_CALL, versionFailure));
                } else {
                    line = evaluate(run, scene, repetition, provider, model, version, seedMillis, seeded, agent);
                }
                run.write(line);
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    private Line evaluate(EvaluationRun run, SeedEvaluationScene scene, int repetition, String provider, String model, String version,
                          double seedMillis, SeedEvaluationSeeder.SeededScene seeded,
                          RecallEvaluationRunner.EvaluationAgent agent) {
        UUID executionId = null;
        RecallAgentInput input = null;
        LlmRecallAgent.Invocation invocation = null;
        Double preparationMillis = null;
        Double agentMillis = null;
        Verdict verdict = new Verdict(false, List.of("NOT_VALIDATED"));
        Failure failure = null;
        RecallTraceCollector trace = null;
        RecallTraceCollector.Span preparing = null;
        RecallTraceCollector.Span calling = null;
        RecallTraceCollector.Span validating = null;
        Stage stage = Stage.CANDIDATE_PREPARATION;
        long started = System.nanoTime();
        try {
            var execution = seeder.createExecution(scene, seeded, version);
            executionId = execution.executionId();
            trace = traces.start(executionId);
            preparing = trace.begin(RecallTraceCollector.Step.CANDIDATE_PREPARATION, "recall-candidates");
            input = inputs.prepare(executionId);
            trace.candidates(preparing, input, null);
            preparationMillis = elapsed(started);
            stage = Stage.MODEL_CALL;
            started = System.nanoTime();
            if (!agent.recordsModelCalls()) {
                calling = trace.begin(RecallTraceCollector.Step.MODEL_CALL, model);
            }
            invocation = agent.invoke(executionId, input, trace);
            agentMillis = elapsed(started);
            if (invocation == null || invocation.rawResponse() == null || invocation.rawResponse().isBlank()) {
                throw new IllegalStateException("Missing or empty model response");
            }
            stage = Stage.CONVERSION;
            if (invocation.result() == null || invocation.result().result() == null) {
                throw new IllegalStateException("Missing converted recall result");
            }
            if (calling != null) {
                trace.model(calling, model, input, invocation.responseTimeMillis(), invocation.inputTokens(),
                        invocation.outputTokens(), null);
            }
            stage = Stage.VALIDATION;
            validating = trace.begin(RecallTraceCollector.Step.VALIDATION, "proposal");
            verdict = validate(seeded.memberId(), execution.triggerPlaceId(), invocation.result());
            trace.validation(validating, verdict.passed(), null);
            if (!verdict.passed()) {
                failure = new Failure(stage, String.join(", ", verdict.errors()));
            }
        } catch (LlmRecallAgent.CallException exception) {
            invocation = exception.invocation();
            agentMillis = elapsed(started);
            stage = invocation != null && invocation.rawResponse() != null && !invocation.rawResponse().isBlank()
                    ? Stage.CONVERSION : Stage.MODEL_CALL;
            failure = failure(stage, exception);
            verdict = new Verdict(false, List.of(stage.name()));
            if (trace != null && calling != null) {
                trace.model(calling, model, input, invocation == null ? null : invocation.responseTimeMillis(),
                        invocation == null ? null : invocation.inputTokens(),
                        invocation == null ? null : invocation.outputTokens(), exception);
            }
        } catch (Exception exception) {
            if (stage == Stage.CANDIDATE_PREPARATION) {
                preparationMillis = elapsed(started);
            } else if (stage == Stage.MODEL_CALL) {
                agentMillis = elapsed(started);
            }
            failure = failure(stage, exception);
            verdict = new Verdict(false, List.of(stage.name()));
            if (trace != null) {
                if (stage == Stage.CANDIDATE_PREPARATION && preparing != null) {
                    trace.candidates(preparing, input, exception);
                } else if ((stage == Stage.MODEL_CALL || stage == Stage.CONVERSION) && calling != null) {
                    trace.model(calling, model, input, invocation == null ? null : invocation.responseTimeMillis(),
                            invocation == null ? null : invocation.inputTokens(),
                            invocation == null ? null : invocation.outputTokens(), exception);
                } else if (stage == Stage.VALIDATION && validating != null) {
                    trace.validation(validating, false, exception);
                }
            }
        }
        return new Line(run.runId(), run.startedAt(), scene.id(), scene.split(), scene.kind(), repetition, provider, model, version, executionId, input,
                invocation == null ? null : invocation.rawResponse(), invocation == null ? null : invocation.result(),
                verdict, seedMillis, preparationMillis, agentMillis,
                invocation == null ? null : invocation.inputTokens(), invocation == null ? null : invocation.outputTokens(),
                failure, trace == null ? List.of() : trace.entries());
    }

    private Verdict validate(UUID memberId, UUID triggerPlaceId, RecallAgentResult result) {
        if (result.result() == RecallExecutionResult.NO_ACTION) {
            var items = result.proposalPayload() == null ? null : result.proposalPayload().items();
            boolean valid = result.proposalType() == null && (items == null || items.isEmpty());
            return new Verdict(valid, valid ? List.of() : List.of("INVALID_NO_ACTION_SHAPE"));
        }
        boolean valid = validator.valid(memberId, triggerPlaceId, result);
        return new Verdict(valid, valid ? List.of() : List.of("INVALID_PROPOSAL"));
    }

    public static List<Line> recordFailure(List<SeedEvaluationScene> scenes, int repetitions,
                                           String provider, String model, Stage stage, Exception exception, Path output)
            throws IOException {
        try (var run = EvaluationRun.start(output)) {
            return recordFailure(scenes, repetitions, provider, model, stage, exception, run);
        }
    }

    public static List<Line> recordFailure(List<SeedEvaluationScene> scenes, int repetitions,
                                           String provider, String model, Stage stage, Exception exception, EvaluationRun run)
            throws IOException {
        var lines = new ArrayList<Line>();
        for (var scene : scenes) {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                var line = failed(run, scene, repetition, provider, model, null, null,
                        failure(stage, exception));
                run.write(line);
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    public static void printSummary(List<Line> lines, PrintStream output) {
        lines.stream().map(line -> line.provider() + "/" + line.model()).distinct().forEach(model -> {
            var runs = lines.stream().filter(line -> (line.provider() + "/" + line.model()).equals(model)).toList();
            long passed = runs.stream().filter(line -> line.verdict().passed()).count();
            output.printf(Locale.ROOT, "%s runs=%d passed=%d validityPassRate=%.1f%%%n",
                    model, runs.size(), passed, passed * 100.0 / runs.size());
            output.println("stageFailures=" + Arrays.stream(Stage.values()).map(stage -> stage + ":"
                    + runs.stream().filter(line -> line.failure() != null && line.failure().stage() == stage).count())
                    .toList());
            output.println("distribution=" + List.of("NO_ACTION", "PLACE", "COURSE", "CONTENT", "NO_RESULT",
                    "PROPOSE_WITHOUT_TYPE").stream().map(kind -> kind + ":"
                    + runs.stream().filter(line -> kind.equals(distribution(line))).count()).toList());
            output.printf(Locale.ROOT, "avgSeed=%sms avgCandidatePreparation=%sms avgAgentCall=%sms "
                            + "avgInputTokens=%s avgOutputTokens=%s%n",
                    average(runs, Line::seedTimeMillis), average(runs, Line::candidatePreparationTimeMillis),
                    average(runs, Line::agentCallTimeMillis), average(runs, Line::inputTokens),
                    average(runs, Line::outputTokens));
        });
        output.println("Validity measures candidate construction and result structure, not decision quality. "
                + "Decision quality requires a future golden set.");
    }

    private static String distribution(Line line) {
        if (line.result() == null || line.result().result() == null) {
            return "NO_RESULT";
        }
        if (line.result().result() == RecallExecutionResult.NO_ACTION) {
            return "NO_ACTION";
        }
        return line.result().proposalType() == null ? "PROPOSE_WITHOUT_TYPE" : line.result().proposalType().name();
    }

    private static String average(List<Line> lines, Function<Line, ? extends Number> value) {
        var average = lines.stream().map(value).filter(number -> number != null)
                .mapToDouble(Number::doubleValue).average();
        return average.isPresent() ? String.format(Locale.ROOT, "%.1f", average.getAsDouble()) : "n/a";
    }

    private static Line failed(EvaluationRun run, SeedEvaluationScene scene, int repetition, String provider, String model, String version,
                                Double seedMillis, Failure failure) {
        return new Line(run.runId(), run.startedAt(), scene.id(), scene.split(), scene.kind(), repetition, provider, model, version, null, null, null, null,
                new Verdict(false, List.of(failure.stage().name())), seedMillis, null, null, null, null, failure, List.of());
    }

    private static Failure failure(Stage stage, Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return new Failure(stage, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    private static double elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    public enum Stage { SEED, CANDIDATE_PREPARATION, MODEL_CALL, CONVERSION, VALIDATION }

    public record Failure(Stage stage, String message) {}

    public record Verdict(boolean passed, List<String> errors) {}

    public record Line(UUID runId, Instant runStartedAt, String sceneId, String split, String kind,
                       int repetition, String provider, String model, String agentVersion,
                       UUID executionId, RecallAgentInput input, String rawResponse, RecallAgentResult result,
                       Verdict verdict, Double seedTimeMillis, Double candidatePreparationTimeMillis,
                       Double agentCallTimeMillis, Integer inputTokens, Integer outputTokens, Failure failure,
                       List<RecallTraceCollector.Entry> trace) {}
}
