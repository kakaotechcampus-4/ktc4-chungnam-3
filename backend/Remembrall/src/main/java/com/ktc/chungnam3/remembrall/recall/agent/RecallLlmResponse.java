package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record RecallLlmResponse(
        RecallExecutionResult result,
        @Nullable ProposalType proposalType,
        List<Item> items,
        @Nullable String decisionSummary,
        @Nullable String title,
        @Nullable String body
) {
    public RecallAgentResult toAgentResult() {
        if (result == null) {
            throw new IllegalArgumentException("Missing recall result");
        }
        var payload = items == null ? null : new ProposalPayload(items.stream()
                .map(item -> item == null ? null : new ProposalPayload.Item(item.contentId(), item.placeId(), item.source()))
                .toList());
        return new RecallAgentResult(result, proposalType, payload, decisionSummary, title, body);
    }

    public record Item(UUID contentId, @Nullable UUID placeId, ProposalSource source) {}
}
