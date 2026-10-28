package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.config.RecallAgentExecutionProperties;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.function.LongSupplier;

public final class RecallAgentSession {
    public static final String CONTEXT_KEY = "recall.session";
    private final RecallAgentExecutionProperties properties;
    private final RecallTraceCollector trace;
    private final LongSupplier nanoTime;
    private final long started;
    private int toolCalls;
    private Integer inputTokens;
    private Integer outputTokens;
    private boolean unknownInput;
    private boolean unknownOutput;
    private boolean limitRecorded;

    public RecallAgentSession(RecallAgentExecutionProperties properties, RecallTraceCollector trace, LongSupplier nanoTime) {
        this.properties = properties;
        this.trace = trace;
        this.nanoTime = nanoTime;
        this.started = nanoTime.getAsLong();
    }

    public void checkTime() {
        if (nanoTime.getAsLong() - started >= properties.executionTimeout().toNanos()) {
            var error = new ExecutionTimeout();
            if (trace != null) {
                trace.begin(RecallTraceCollector.Step.VALIDATION, "agent-execution-timeout")
                        .finish(false, error, null, null, null, Map.of("reason", "AGENT_EXECUTION_TIMEOUT"));
            }
            throw error;
        }
    }

    public void beforeModel() {
        checkTime();
        if (properties.toolsEnabled() && toolCalls >= properties.maxToolCalls()) {
            stopAtToolLimit();
        }
    }

    public void beforeTool() {
        checkTime();
        if (toolCalls >= properties.maxToolCalls()) {
            stopAtToolLimit();
        }
        toolCalls++;
    }

    private void stopAtToolLimit() {
        if (!limitRecorded && trace != null) {
            trace.begin(RecallTraceCollector.Step.VALIDATION, "tool-call-limit")
                    .finish(true, null, null, null, null, Map.of("reason", "TOOL_CALL_LIMIT_REACHED",
                            "toolCalls", toolCalls, "maxToolCalls", properties.maxToolCalls()));
        }
        limitRecorded = true;
        throw new ToolCallLimit();
    }

    public void account(ChatResponse response) {
        var usage = response == null ? null : response.getMetadata().getUsage();
        Integer input = usage == null || usage instanceof EmptyUsage ? null : usage.getPromptTokens();
        Integer output = usage == null || usage instanceof EmptyUsage ? null : usage.getCompletionTokens();
        unknownInput |= input == null;
        unknownOutput |= output == null;
        if (input != null) {
            inputTokens = (inputTokens == null ? 0 : inputTokens) + input;
        }
        if (output != null) {
            outputTokens = (outputTokens == null ? 0 : outputTokens) + output;
        }
    }

    public RecallTraceCollector trace() { return trace; }
    public int maxResponseBytes() { return properties.maxToolResponseBytes(); }
    public double elapsedMillis() { return (nanoTime.getAsLong() - started) / 1_000_000.0; }
    public Integer inputTokens() { return unknownInput ? null : inputTokens; }
    public Integer outputTokens() { return unknownOutput ? null : outputTokens; }

    public static final class ToolCallLimit extends RuntimeException {
        public ToolCallLimit() { super("TOOL_CALL_LIMIT_REACHED"); }
    }

    public static final class ExecutionTimeout extends RuntimeException {
        public ExecutionTimeout() { super("AGENT_EXECUTION_TIMEOUT"); }
    }
}
