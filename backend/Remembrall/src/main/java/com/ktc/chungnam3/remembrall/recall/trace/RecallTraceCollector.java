package com.ktc.chungnam3.remembrall.recall.trace;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecallTraceCollector {
    private final UUID executionId;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final RecallTraceProperties properties;
    private final List<Entry> entries = new ArrayList<>();
    private int sequence;

    public RecallTraceCollector(UUID executionId, Clock clock, ObjectMapper mapper, RecallTraceProperties properties) {
        this.executionId = executionId;
        this.clock = clock;
        this.mapper = mapper;
        this.properties = properties;
    }

    public UUID executionId() {
        return executionId;
    }

    public synchronized Span begin(Step step, String name) {
        return new Span(++sequence, step, clip(name, 200), clock.instant(), System.nanoTime());
    }

    public synchronized List<Entry> entries() {
        return entries.stream().sorted(Comparator.comparingInt(Entry::seq)).toList();
    }

    public int maxDetailBytes() { return properties.maxDetailBytes(); }

    public synchronized void modelConversionFailed(Throwable error) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            var entry = entries.get(index);
            if (entry.step() == Step.MODEL_CALL) {
                entries.set(index, new Entry(entry.id(), entry.executionId(), entry.seq(), entry.step(), entry.name(),
                        entry.startedAt(), entry.durationMs(), false, errorMessage(error), entry.inputTokens(),
                        entry.outputTokens(), entry.detail()));
                return;
            }
        }
    }

    public void candidates(Span span, RecallAgentInput input, Throwable error) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("originCount", input == null || input.origin() == null ? 0 : 1);
        detail.put("originContentCount", input == null || input.origin() == null ? 0 : input.origin().contents().size());
        detail.put("nearbyPersonalCount", input == null ? 0 : input.nearbyPersonalCandidates().size());
        detail.put("nearbyPublicCount", input == null ? 0 : input.nearbyPublicCandidates().size());
        detail.put("semanticCount", input == null ? 0 : input.semanticCandidates().size());
        span.finish(error == null, error, null, null, null, detail);
    }

    public void model(Span span, String model, RecallAgentInput input, Double durationMillis,
                       Integer inputTokens, Integer outputTokens, Throwable error) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("model", model);
        detail.put("inputTokens", inputTokens);
        detail.put("outputTokens", outputTokens);
        if (properties.includeAgentInput() && input != null) {
            detail.put("agentInput", input);
        }
        span.finish(error == null, error, durationMillis, inputTokens, outputTokens, detail);
    }

    public void validation(Span span, boolean passed, Throwable error) {
        span.finish(passed && error == null, error, null, null, null, Map.of("passed", passed));
    }

    public void notification(Span span, UUID notificationId, String status, String failureCode, Throwable error) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("notificationId", notificationId);
        detail.put("status", status);
        detail.put("failureCode", failureCode);
        Throwable failure = error != null ? error : "FAILED".equals(status) ? new IllegalStateException(failureCode) : null;
        span.finish(failure == null && status != null, failure, null, null, null, detail);
    }

    public void recordToolCall(String name, Instant startedAt, double durationMillis, boolean success,
                                Throwable error, Object arguments, Object response) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("toolName", name);
        detail.put("arguments", arguments);
        detail.put("response", response);
        var span = begin(Step.TOOL_CALL, name);
        span.startedAt = startedAt;
        span.finish(success, error, durationMillis, null, null, detail);
    }

    private JsonNode boundedDetail(Object value) {
        try {
            JsonNode detail = mapper.valueToTree(value);
            byte[] bytes = mapper.writeValueAsBytes(detail);
            if (bytes.length <= properties.maxDetailBytes()) {
                return detail;
            }
            String source = mapper.writeValueAsString(detail);
            ObjectNode truncated = mapper.createObjectNode().put("truncated", true).put("originalBytes", bytes.length);
            int low = 0;
            int high = source.codePointCount(0, source.length());
            while (low < high) {
                int middle = (low + high + 1) / 2;
                truncated.put("preview", source.substring(0, source.offsetByCodePoints(0, middle)));
                if (mapper.writeValueAsBytes(truncated).length <= properties.maxDetailBytes()) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return truncated.put("preview", source.substring(0, source.offsetByCodePoints(0, low)));
        } catch (RuntimeException exception) {
            return mapper.createObjectNode().put("serializationFailed", true);
        }
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return clip(cause.getClass().getSimpleName() + ": " + cause.getMessage(), properties.maxErrorLength());
    }

    private static String clip(String value, int limit) {
        if (value == null || value.codePointCount(0, value.length()) <= limit) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, limit - 1)) + "…";
    }

    private static Integer tokens(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    public final class Span {
        private final int seq;
        private final Step step;
        private final String name;
        private Instant startedAt;
        private final long nanoStarted;
        private boolean finished;

        private Span(int seq, Step step, String name, Instant startedAt, long nanoStarted) {
            this.seq = seq;
            this.step = step;
            this.name = name;
            this.startedAt = startedAt;
            this.nanoStarted = nanoStarted;
        }

        public void finish(boolean success, Throwable error, Double durationMillis,
                             Integer inputTokens, Integer outputTokens, Object detail) {
            synchronized (RecallTraceCollector.this) {
                if (finished) {
                    return;
                }
                finished = true;
                double duration = durationMillis != null && Double.isFinite(durationMillis) && durationMillis >= 0
                        ? durationMillis : Math.max(0, (System.nanoTime() - nanoStarted) / 1_000_000.0);
                entries.add(new Entry(UUID.randomUUID(), executionId, seq, step, name, startedAt, duration,
                        success, errorMessage(error), tokens(inputTokens), tokens(outputTokens), boundedDetail(detail)));
            }
        }
    }

    public enum Step { CANDIDATE_PREPARATION, MODEL_CALL, TOOL_CALL, VALIDATION, NOTIFICATION_CREATE, NOTIFICATION_SEND }

    public record Entry(UUID id, UUID executionId, int seq, Step step, String name, Instant startedAt,
                        double durationMs, boolean success, String errorMessage, Integer inputTokens,
                        Integer outputTokens, JsonNode detail) {}
}
