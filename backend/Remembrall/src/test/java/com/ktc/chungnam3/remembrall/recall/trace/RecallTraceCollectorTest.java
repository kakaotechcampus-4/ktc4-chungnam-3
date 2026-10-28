package com.ktc.chungnam3.remembrall.recall.trace;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import com.ktc.chungnam3.remembrall.e2e.E2eAgentInputRecorder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecallTraceCollectorTest {
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void recordsBeginOrderAndPreservesToolArgumentsAndActualResponse() {
        var trace = collector(16384, 1000, false);
        var candidate = trace.begin(RecallTraceCollector.Step.CANDIDATE_PREPARATION, "candidates");
        trace.candidates(candidate, input(), null);
        var model = trace.begin(RecallTraceCollector.Step.MODEL_CALL, "test-model");
        var arguments = Map.of("query", "둔산 카페", "radiusMeters", 1000);
        String response = "{ \"places\": [\"카페\"] }\n";
        trace.recordToolCall("searchPlaces", NOW, 3.25, true, null, arguments, response);
        trace.model(model, "test-model", input(), 12.5, 120, 30, null);
        trace.validation(trace.begin(RecallTraceCollector.Step.VALIDATION, "proposal"), true, null);
        var entries = trace.entries();
        assertThat(entries).extracting(RecallTraceCollector.Entry::seq).containsExactly(1, 2, 3, 4);
        assertThat(entries).extracting(RecallTraceCollector.Entry::step).containsExactly(
                RecallTraceCollector.Step.CANDIDATE_PREPARATION, RecallTraceCollector.Step.MODEL_CALL,
                RecallTraceCollector.Step.TOOL_CALL, RecallTraceCollector.Step.VALIDATION);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.executionId()).isEqualTo(trace.executionId());
            assertThat(entry.startedAt()).isEqualTo(NOW);
            assertThat(entry.durationMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(entries.get(1).durationMs()).isEqualTo(12.5);
        assertThat(entries.get(1).inputTokens()).isEqualTo(120);
        assertThat(entries.get(1).outputTokens()).isEqualTo(30);
        assertThat(entries.get(1).detail().has("agentInput")).isFalse();
        assertThat(entries.get(2).detail().get("arguments")).isEqualTo(mapper.valueToTree(arguments));
        assertThat(entries.get(2).detail().get("response").asText()).isEqualTo(response);
        assertThat(entries.get(2).name()).isEqualTo("searchPlaces");
        assertThat(entries.getFirst().detail().get("originCount").asInt()).isEqualTo(1);
        assertThat(entries.getFirst().detail().get("originContentCount").asInt()).isZero();
    }

    @Test
    void fullAgentInputIsOptInAndIsStillSubjectToTheSerializedUtf8ByteLimit() {
        var enabled = collector(16384, 1000, true);
        enabled.model(enabled.begin(RecallTraceCollector.Step.MODEL_CALL, "model"), "model", input(), 1.0, 1, 2, null);
        assertThat(enabled.entries().getFirst().detail().get("agentInput")).isEqualTo(mapper.valueToTree(input()));
        var limited = collector(256, 1000, true);
        limited.model(limited.begin(RecallTraceCollector.Step.MODEL_CALL, "model"), "model", input(), 1.0, 1, 2, null);
        var detail = limited.entries().getFirst().detail();
        assertThat(detail.get("truncated").asBoolean()).isTrue();
        assertThat(detail.get("originalBytes").asInt()).isGreaterThan(256);
        assertThat(mapper.writeValueAsBytes(detail)).hasSizeLessThanOrEqualTo(256);
    }

    @Test
    void truncatesUnicodeDetailsAndErrorsWithoutInvalidJsonOrBrokenSurrogatePairs() {
        var trace = collector(256, 32, false);
        trace.recordToolCall("tool", NOW, 1, false, new IllegalStateException("오류😀".repeat(100)),
                Map.of("query", "카페😀".repeat(300)), "result😀".repeat(300));
        var entry = trace.entries().getFirst();
        assertThat(entry.success()).isFalse();
        assertThat(entry.errorMessage().codePointCount(0, entry.errorMessage().length())).isEqualTo(32);
        assertThat(entry.errorMessage()).endsWith("…");
        assertThat(entry.detail().get("truncated").asBoolean()).isTrue();
        assertThat(mapper.writeValueAsBytes(entry.detail())).hasSizeLessThanOrEqualTo(256);
        assertThat(mapper.readTree(mapper.writeValueAsBytes(entry.detail()))).isEqualTo(entry.detail());
    }

    @Test
    void finishingASpanTwiceDoesNotDuplicateItAndSerializationFailureIsContained() {
        var trace = collector(256, 1000, false);
        var span = trace.begin(RecallTraceCollector.Step.VALIDATION, "validation");
        span.finish(true, null, null, null, null, new Unserializable());
        span.finish(false, new IllegalStateException("late failure"), null, null, null, Map.of());
        assertThat(trace.entries()).hasSize(1);
        assertThat(trace.entries().getFirst().success()).isTrue();
        assertThat(trace.entries().getFirst().detail().get("serializationFailed").asBoolean()).isTrue();
    }

    @Test
    void temporaryAgentRecordsTimeWithoutInventingTokenUsage() throws Exception {
        var result = new StubRecallAgent().executeWithStatistics(UUID.randomUUID(), input());
        assertThat(result.result().result().name()).isEqualTo("NO_ACTION");
        assertThat(result.responseTimeMillis()).isGreaterThanOrEqualTo(0);
        assertThat(result.inputTokens()).isNull();
        assertThat(result.outputTokens()).isNull();
    }

    @Test
    void e2eRecorderForwardsStatisticsAndModelNameWhileKeepingTheActualInput() throws Exception {
        var recorder = new E2eAgentInputRecorder();
        var delegate = new RecallAgent() {
            @Override
            public String version() {
                return "test-version";
            }

            @Override
            public RecallAgentResult execute(UUID id, RecallAgentInput input) {
                return RecallAgentResult.noAction();
            }

            @Override
            public Execution executeWithStatistics(UUID id, RecallAgentInput input) {
                return new Execution(RecallAgentResult.noAction(), 12.5, 123, 45);
            }

            @Override
            public String modelName() {
                return "actual-model";
            }
        };
        var wrapped = (RecallAgent) recorder.postProcessAfterInitialization(delegate, "agent");
        UUID id = UUID.randomUUID();
        var result = wrapped.executeWithStatistics(id, input());
        assertThat(result.responseTimeMillis()).isEqualTo(12.5);
        assertThat(result.inputTokens()).isEqualTo(123);
        assertThat(result.outputTokens()).isEqualTo(45);
        assertThat(wrapped.modelName()).isEqualTo("actual-model");
        assertThat(recorder.find(id)).contains(input());
    }

    private RecallTraceCollector collector(int bytes, int errorLength, boolean includeInput) {
        return new RecallTraceCollector(UUID.randomUUID(), Clock.fixed(NOW, ZoneOffset.UTC), mapper,
                new RecallTraceProperties(bytes, errorLength, includeInput));
    }

    private RecallAgentInput input() {
        return new RecallAgentInput(new RecallAgentInput.Origin(new UUID(0, 1), "카페", 36.351, 127.38, List.of()),
                List.of(), List.of(), List.of(), new RecallAgentInput.Situation(
                LocalDateTime.parse("2026-10-21T12:00:00"), DayOfWeek.WEDNESDAY));
    }

    public static class Unserializable {
        public String getValue() {
            throw new IllegalStateException("Cannot serialize");
        }
    }
}
