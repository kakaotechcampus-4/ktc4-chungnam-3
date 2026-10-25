package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RecallCandidateHistory {
    public static final String CONTEXT_KEY = "recall.seen-candidates";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final Set<UUID> contentIds = new HashSet<>();
    private final Set<UUID> placeIds = new HashSet<>();

    public RecallCandidateHistory(RecallAgentInput input) {
        observe(MAPPER.valueToTree(input));
    }

    public synchronized void observe(JsonNode node) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                if (entry.getValue().isString() && Set.of("contentId", "placeId").contains(entry.getKey())) {
                    try {
                        var id = UUID.fromString(entry.getValue().asText());
                        (entry.getKey().equals("contentId") ? contentIds : placeIds).add(id);
                    } catch (IllegalArgumentException ignored) {
                    }
                } else {
                    observe(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::observe);
        }
    }

    public synchronized Set<UUID> contentIds() { return Set.copyOf(contentIds); }
    public synchronized Set<UUID> placeIds() { return Set.copyOf(placeIds); }
}
