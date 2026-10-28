package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import java.util.UUID;

public interface RecallAgent {

    String version();

    RecallAgentResult execute(UUID executionId, RecallAgentInput input) throws Exception;

    default Execution executeWithStatistics(UUID executionId, RecallAgentInput input) throws Exception {
        long started = System.nanoTime();
        RecallAgentResult result = execute(executionId, input);
        return new Execution(result, (System.nanoTime() - started) / 1_000_000.0, null, null);
    }

    default String modelName() {
        return version();
    }

    default boolean recordsModelCalls() { return false; }

    default Execution executeWithStatistics(UUID executionId, RecallAgentInput input, RecallTraceCollector trace) throws Exception {
        return executeWithStatistics(executionId, input);
    }

    record Execution(RecallAgentResult result, double responseTimeMillis, Integer inputTokens, Integer outputTokens) {}
}
