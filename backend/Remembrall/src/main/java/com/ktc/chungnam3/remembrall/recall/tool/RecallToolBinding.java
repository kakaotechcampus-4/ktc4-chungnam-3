package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentSession;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.SearchSource;

@Component
public class RecallToolBinding {
    public static final String SCOPE_KEY = "recall.scope";
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final RecallToolService tools;
    private final RecallCandidateRepository candidates;

    public RecallToolBinding(RecallToolService tools, RecallCandidateRepository candidates) {
        this.tools = tools;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public RecallToolScope scope(UUID executionId) {
        var rows = candidates.findOrigin(executionId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Recall execution scope no longer exists");
        }
        var row = rows.getFirst();
        return new RecallToolScope(row.memberId(), row.placeId(), row.latitude(), row.longitude());
    }

    public ToolCallback[] callbacks() {
        return new ToolCallback[]{
                callback("checkVisitConditions", "잠정 선택한 장소를 제안하기 전에 전체 요약과 장소 소개의 운영 조건, 최근 SENT 알림과 열람 여부, 현재 날씨를 확인한다. 최대 3개 장소. CONTENT와 NO_ACTION에는 필요 없다.",
                        VisitArguments.class, (scope, args, history) -> tools.checkVisitConditions(scope, args.placeIds())),
                callback("searchCandidates", "선택한 장소가 휴무·종료·날씨 등으로 부적합하거나 후보가 부족할 때 목적·반경·개인/공용 범위로 대안을 찾는다. 이번 실행에서 이미 본 콘텐츠와 장소는 서버가 자동 제외한다. 새 장소도 checkVisitConditions로 확인한다.",
                        SearchArguments.class, (scope, args, history) -> tools.searchCandidates(scope, args.condition(),
                                args.radiusMeters(), args.source(), history.contentIds(), history.placeIds()))
        };
    }

    private <T> ToolCallback callback(String name, String description, Class<T> type,
                                      Operation<T> operation) {
        var definition = ToolDefinition.builder().name(name).description(description)
                .inputSchema(JsonSchemaGenerator.generateForType(type)).build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() { return definition; }

            @Override
            public String call(String arguments) {
                return MAPPER.writeValueAsString(Map.of("error", "서버 실행 범위가 필요합니다."));
            }

            @Override
            public String call(String arguments, ToolContext context) {
                if (context == null || !(context.getContext().get(SCOPE_KEY) instanceof RecallToolScope scope)
                        || !(context.getContext().get(RecallAgentSession.CONTEXT_KEY) instanceof RecallAgentSession session)
                        || !(context.getContext().get(RecallCandidateHistory.CONTEXT_KEY) instanceof RecallCandidateHistory history)) {
                    return call(arguments);
                }
                synchronized (history) {
                    session.beforeTool();
                    long started = System.nanoTime();
                    Instant startedAt = Instant.now();
                    JsonNode parsedArguments = MAPPER.getNodeFactory().stringNode(arguments);
                    Object result;
                    Throwable failure = null;
                    try {
                        parsedArguments = MAPPER.readTree(arguments);
                        result = operation.apply(scope, MAPPER.treeToValue(parsedArguments, type), history);
                    } catch (RuntimeException exception) {
                        failure = exception;
                        result = Map.of("error", "유효한 도구 요청을 처리할 수 없습니다.");
                    }
                    int responseLimit = session.maxResponseBytes();
                    if (session.trace() != null) {
                        int overhead = MAPPER.writeValueAsBytes(Map.of("toolName", name, "arguments", parsedArguments, "response", "")).length;
                        responseLimit = Math.min(responseLimit, Math.max(128, (session.trace().maxDetailBytes() - overhead) / 2));
                    }
                    String response = boundedResponse(result, responseLimit);
                    history.observe(MAPPER.readTree(response));
                    boolean success = failure == null && successful(MAPPER.readTree(response));
                    if (session.trace() != null) {
                        session.trace().recordToolCall(name, startedAt, (System.nanoTime() - started) / 1_000_000.0,
                                success, failure, parsedArguments, response);
                    }
                    session.checkTime();
                    return response;
                }
            }
        };
    }

    private static boolean successful(JsonNode response) {
        return (response.path("error").isMissingNode() || response.path("error").isNull())
                && (!response.path("errors").isArray() || response.path("errors").isEmpty());
    }

    static String boundedResponse(Object result, int limit) {
        ObjectNode json = MAPPER.valueToTree(result);
        if (MAPPER.writeValueAsBytes(json).length <= limit) {
            return MAPPER.writeValueAsString(json);
        }
        json.put("truncated", true);
        while (MAPPER.writeValueAsBytes(json).length > limit) {
            ArrayNode largest = largestArray(json);
            if (largest == null) {
                return MAPPER.writeValueAsString(Map.of("error", "도구 응답이 너무 큽니다.", "truncated", true));
            }
            largest.remove(largest.size() - 1);
        }
        return MAPPER.writeValueAsString(json);
    }

    private static ArrayNode largestArray(JsonNode node) {
        ArrayNode largest = node.isArray() && !node.isEmpty() ? (ArrayNode) node : null;
        for (JsonNode child : node) {
            ArrayNode candidate = largestArray(child);
            if (candidate != null && (largest == null || MAPPER.writeValueAsBytes(candidate).length > MAPPER.writeValueAsBytes(largest).length)) {
                largest = candidate;
            }
        }
        return largest;
    }

    public record SearchArguments(
            @ToolParam(description = "지금 찾는 후보의 음식·활동·분위기 등을 설명하는 비어 있지 않은 자연어 조건") String condition,
            @ToolParam(required = false, description = "선택 검색 반경(미터). 생략하면 기본 반경이며 최대 설정 반경으로 제한된다") Double radiusMeters,
            @ToolParam(required = false, description = "검색 출처: PERSONAL(개인), PUBLIC(공용), ALL(전체). 생략하면 ALL") SearchSource source) {}
    public record VisitArguments(
            @ToolParam(description = "방문을 잠정 선택한 장소 UUID 목록. 한 번에 최대 3개") List<UUID> placeIds) {}

    @FunctionalInterface
    private interface Operation<T> {
        Object apply(RecallToolScope scope, T arguments, RecallCandidateHistory history);
    }
}
