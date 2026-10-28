package com.ktc.chungnam3.remembrall.recall.agent;

import java.util.UUID;

public class StubRecallAgent implements RecallAgent {
    private final RecallAgentResult result;

    public StubRecallAgent() {
        this(RecallAgentResult.noAction());
    }

    public StubRecallAgent(RecallAgentResult result) {
        this.result = result;
    }

    @Override
    public String version() {
        return "stub-v1";
    }

    @Override
    public RecallAgentResult execute(UUID executionId, RecallAgentInput input) {
        return result;
    }
}
