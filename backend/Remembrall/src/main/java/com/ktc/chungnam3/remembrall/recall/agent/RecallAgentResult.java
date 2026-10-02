package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;

public record RecallAgentResult(RecallExecutionResult result, ProposalType proposalType,
                               ProposalPayload proposalPayload, String decisionSummary,
                               String title, String body) {
    public static RecallAgentResult noAction() {
        return new RecallAgentResult(RecallExecutionResult.NO_ACTION, null, null, null, null, null);
    }

    public static RecallAgentResult propose(ProposalType type, ProposalPayload payload,
                                            String summary, String title, String body) {
        return new RecallAgentResult(RecallExecutionResult.PROPOSE, type, payload, summary, title, body);
    }
}
