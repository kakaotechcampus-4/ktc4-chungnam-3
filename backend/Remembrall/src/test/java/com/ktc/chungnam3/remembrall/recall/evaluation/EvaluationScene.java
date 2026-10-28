package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;

import java.util.List;
import java.util.UUID;

public record EvaluationScene(String id, RecallAgentInput input, Expectation expectation) {
    public EvaluationScene {
        if (id == null || id.isBlank() || input == null || expectation == null) {
            throw new IllegalArgumentException("Incomplete evaluation scene");
        }
    }

    public record Expectation(List<RecallExecutionResult> results, List<ProposalType> proposalTypes,
                              List<UUID> requiredContentIds, List<UUID> requiredPlaceIds,
                              List<UUID> forbiddenContentIds, List<UUID> forbiddenPlaceIds, boolean allowPublic) {
        public Expectation {
            if (results == null || results.isEmpty()) {
                throw new IllegalArgumentException("Expected results are required");
            }
            results = List.copyOf(results);
            proposalTypes = proposalTypes == null ? List.of() : List.copyOf(proposalTypes);
            requiredContentIds = requiredContentIds == null ? List.of() : List.copyOf(requiredContentIds);
            requiredPlaceIds = requiredPlaceIds == null ? List.of() : List.copyOf(requiredPlaceIds);
            forbiddenContentIds = forbiddenContentIds == null ? List.of() : List.copyOf(forbiddenContentIds);
            forbiddenPlaceIds = forbiddenPlaceIds == null ? List.of() : List.copyOf(forbiddenPlaceIds);
        }
    }
}
