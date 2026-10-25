package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

@FunctionalInterface
public interface JudgeModel extends AutoCloseable {
    Response call(String systemPrompt, String userMessage) throws Exception;

    @Override
    default void close() throws Exception {}

    record Response(String rawResponse, double durationMillis, Integer inputTokens, Integer outputTokens) {}
}
