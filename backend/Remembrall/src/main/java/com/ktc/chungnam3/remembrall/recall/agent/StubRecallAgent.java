package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;

import java.util.UUID;

public class StubRecallAgent implements RecallAgent {
    private final RecallAgentResult result;

    public StubRecallAgent() {
        this(RecallAgentResult.noAction());
    }

    public StubRecallAgent(RecallAgentResult result) {
        if (result.proposalPayload() != null && result.proposalPayload().items().stream()
                .anyMatch(item -> item.source() != ProposalSource.PERSONAL)) {
            throw new IllegalArgumentException("Stub proposals must only contain PERSONAL items");
        }
        this.result = result;
    }

    @Override
    public RecallAgentResult execute(UUID executionId, LocationEventRequest event) {
        return result;
    }
}
