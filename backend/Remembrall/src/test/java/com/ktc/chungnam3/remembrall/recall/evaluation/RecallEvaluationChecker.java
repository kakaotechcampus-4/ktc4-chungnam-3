package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RecallEvaluationChecker {
    public Checks check(EvaluationScene scene, RecallAgentResult result) {
        var errors = new ArrayList<String>();
        if (result == null || result.result() == null) {
            return new Checks(false, false, List.of("MISSING_RESULT"));
        }
        var items = result.proposalPayload() == null ? null : result.proposalPayload().items();
        var usedContentIds = new HashSet<UUID>();
        var usedPlaceIds = new HashSet<UUID>();
        if (result.result() == RecallExecutionResult.NO_ACTION) {
            if (result.proposalType() != null || items != null && !items.isEmpty()) {
                errors.add("INVALID_NO_ACTION_SHAPE");
            }
        } else {
            if (result.proposalType() == null || items == null || items.isEmpty()) {
                errors.add("MISSING_PROPOSAL_FIELDS");
            }
            if (blank(result.decisionSummary()) || blank(result.title()) || blank(result.body())) {
                errors.add("MISSING_PROPOSAL_TEXT");
            }
            if (items != null) {
                if (result.proposalType() == ProposalType.COURSE ? items.size() < 2 : items.size() != 1) {
                    errors.add("INVALID_ITEM_COUNT");
                }
                var sources = inputSources(scene.input());
                var knownPlaces = inputPlaceIds(scene.input());
                for (var item : items) {
                    if (item == null || item.contentId() == null || item.source() == null) {
                        errors.add("INCOMPLETE_ITEM");
                        continue;
                    }
                    usedContentIds.add(item.contentId());
                    var allowedSources = sources.get(item.contentId());
                    if (allowedSources == null) {
                        errors.add("UNKNOWN_CONTENT_ID:" + item.contentId());
                    } else if (!allowedSources.contains(item.source())) {
                        errors.add("SOURCE_MISMATCH:" + item.contentId());
                    }
                    if (result.proposalType() == ProposalType.CONTENT ? item.placeId() != null : item.placeId() == null) {
                        errors.add("INVALID_PLACE_SHAPE");
                    }
                    if (item.placeId() != null) {
                        if (!knownPlaces.contains(item.placeId())) {
                            errors.add("UNKNOWN_PLACE_ID:" + item.placeId());
                        }
                        if (!usedPlaceIds.add(item.placeId()) && result.proposalType() == ProposalType.COURSE) {
                            errors.add("DUPLICATE_COURSE_PLACE");
                        }
                    }
                }
            }
        }
        boolean formatPassed = errors.isEmpty();
        var expected = scene.expectation();
        if (!expected.results().contains(result.result())) {
            errors.add("UNEXPECTED_RESULT");
        }
        if (result.result() == RecallExecutionResult.PROPOSE && !expected.proposalTypes().contains(result.proposalType())) {
            errors.add("UNEXPECTED_PROPOSAL_TYPE");
        }
        if (!usedContentIds.containsAll(expected.requiredContentIds())) {
            errors.add("MISSING_REQUIRED_CONTENT");
        }
        if (!usedPlaceIds.containsAll(expected.requiredPlaceIds())) {
            errors.add("MISSING_REQUIRED_PLACE");
        }
        if (expected.forbiddenContentIds().stream().anyMatch(usedContentIds::contains)) {
            errors.add("FORBIDDEN_CONTENT");
        }
        if (expected.forbiddenPlaceIds().stream().anyMatch(usedPlaceIds::contains)) {
            errors.add("FORBIDDEN_PLACE");
        }
        if (!expected.allowPublic() && items != null && items.stream()
                .anyMatch(item -> item != null && item.source() == ProposalSource.PUBLIC)) {
            errors.add("PUBLIC_NOT_ALLOWED");
        }
        return new Checks(errors.isEmpty(), formatPassed, List.copyOf(errors));
    }

    private Map<UUID, EnumSet<ProposalSource>> inputSources(RecallAgentInput input) {
        var sources = new HashMap<UUID, EnumSet<ProposalSource>>();
        input.origin().contents().forEach(content -> add(sources, content.contentId(), ProposalSource.PERSONAL));
        input.nearbyPersonalCandidates().forEach(place -> place.contents()
                .forEach(content -> add(sources, content.contentId(), ProposalSource.PERSONAL)));
        input.nearbyPublicCandidates().forEach(place -> place.contents()
                .forEach(content -> add(sources, content.contentId(), ProposalSource.PUBLIC)));
        input.semanticCandidates().forEach(content -> add(sources, content.contentId(), content.source()));
        return sources;
    }

    private Set<UUID> inputPlaceIds(RecallAgentInput input) {
        var ids = new HashSet<UUID>();
        ids.add(input.origin().placeId());
        input.nearbyPersonalCandidates().forEach(place -> ids.add(place.placeId()));
        input.nearbyPublicCandidates().forEach(place -> ids.add(place.placeId()));
        return ids;
    }

    private void add(Map<UUID, EnumSet<ProposalSource>> sources, UUID id, ProposalSource source) {
        sources.computeIfAbsent(id, key -> EnumSet.noneOf(ProposalSource.class)).add(source);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Checks(boolean expectationMatched, boolean formatPassed, List<String> errors) {}
}
