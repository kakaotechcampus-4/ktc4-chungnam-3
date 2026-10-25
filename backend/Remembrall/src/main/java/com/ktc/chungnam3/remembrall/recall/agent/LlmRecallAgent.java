package com.ktc.chungnam3.remembrall.recall.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import com.ktc.chungnam3.remembrall.recall.config.RecallAgentExecutionProperties;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import com.ktc.chungnam3.remembrall.recall.tool.RecallCandidateHistory;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public class LlmRecallAgent implements RecallAgent, AutoCloseable {
    private final ChatClient client;
    private final String systemPrompt;
    private final String version;
    private final String modelName;
    private final AutoCloseable resources;
    private final JsonMapper inputMapper;
    private final RecallAgentExecutionProperties execution;
    private final RecallToolBinding tools;
    private final LongSupplier nanoTime;
    private final BeanOutputConverter<RecallLlmResponse> converter = new BeanOutputConverter<>(RecallLlmResponse.class);

    public LlmRecallAgent(ChatClient client, String provider, String model, RecallPrompt prompt,
                          AutoCloseable resources) {
        this(client, provider, model, prompt, resources,
                new RecallAgentExecutionProperties(false, 6, java.time.Duration.ofSeconds(90), 24000), null, System::nanoTime);
    }

    public LlmRecallAgent(ChatClient client, String provider, String model, RecallPrompt prompt,
                          AutoCloseable resources, RecallAgentExecutionProperties execution, RecallToolBinding tools) {
        this(client, provider, model, prompt, resources, execution, tools, System::nanoTime);
    }

    public LlmRecallAgent(ChatClient client, String provider, String model, RecallPrompt prompt,
                          AutoCloseable resources, RecallAgentExecutionProperties execution, RecallToolBinding tools,
                          LongSupplier nanoTime) {
        this.client = client;
        this.systemPrompt = prompt.text();
        this.modelName = model;
        this.version = "llm|" + provider + "|" + model + "|prompt-" + prompt.version() + "-" + promptHash(prompt.text())
                + "|tools-" + (execution.toolsEnabled() ? "on" : "off");
        this.resources = resources;
        this.execution = execution;
        this.tools = tools;
        this.nanoTime = nanoTime;
        if (execution.toolsEnabled() && tools == null) {
            throw new IllegalArgumentException("Recall tools require a server tool binding");
        }
        var module = new SimpleModule();
        module.addSerializer(Instant.class, new ValueSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(value.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime().toString());
            }
        });
        this.inputMapper = JsonMapper.builder().addModule(module).build();
    }

    private static String promptHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 3);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public RecallAgentResult execute(UUID executionId, RecallAgentInput input) {
        return invoke(executionId, input, null).result();
    }

    @Override
    public Execution executeWithStatistics(UUID executionId, RecallAgentInput input) {
        return executeWithStatistics(executionId, input, null);
    }

    @Override
    public Execution executeWithStatistics(UUID executionId, RecallAgentInput input, RecallTraceCollector trace) {
        var invocation = invoke(executionId, input, trace);
        return new Execution(invocation.result(), invocation.responseTimeMillis(),
                invocation.inputTokens(), invocation.outputTokens());
    }

    @Override
    public boolean recordsModelCalls() { return true; }

    @Override
    public String modelName() {
        return modelName;
    }

    public Invocation invoke(RecallAgentInput input) {
        return invoke(null, input, null);
    }

    public Invocation invoke(UUID executionId, RecallAgentInput input, RecallTraceCollector trace) {
        var session = new RecallAgentSession(execution, trace, nanoTime);
        String raw = null;
        try {
            var prompt = new Prompt(List.of(new SystemMessage(systemPrompt + "\n\n" + converter.getFormat()),
                    new UserMessage(inputMapper.writeValueAsString(input))));
            var request = client.prompt(prompt).advisors(new RecallModelCallAdvisor(session, modelName, input));
            if (execution.toolsEnabled()) {
                if (executionId == null) {
                    throw new IllegalArgumentException("Tool-enabled recall requires an execution ID");
                }
                request.advisors(ToolCallingAdvisor.builder().toolCallingManager(ToolCallingManager.builder()
                                .unlimitedCallsPerTool().unlimitedTotalToolCalls().build()).build());
                request.tools((Object[]) tools.callbacks()).toolContext(Map.of(
                        RecallToolBinding.SCOPE_KEY, tools.scope(executionId), RecallAgentSession.CONTEXT_KEY, session,
                        RecallCandidateHistory.CONTEXT_KEY, new RecallCandidateHistory(input)));
            }
            ChatResponse response = request.call().chatResponse();
            session.checkTime();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("Missing recall model response");
            }
            raw = response.getResult().getOutput().getText();
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Empty recall model response");
            }
            int firstBrace = raw.indexOf('{');
            int lastBrace = raw.lastIndexOf('}');
            if (firstBrace < 0 || lastBrace < firstBrace) {
                throw new IllegalStateException("Missing recall JSON object");
            }
            var parsed = converter.convert(raw.substring(firstBrace, lastBrace + 1));
            if (parsed == null) {
                throw new IllegalStateException("Missing structured recall result");
            }
            return new Invocation(raw, parsed.toAgentResult(), session.elapsedMillis(), session.inputTokens(), session.outputTokens());
        } catch (RecallAgentSession.ToolCallLimit exception) {
            var result = new RecallAgentResult(com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult.NO_ACTION,
                    null, null, "TOOL_CALL_LIMIT_REACHED: 도구 호출 상한에 도달해 제안하지 않습니다.", null, null);
            String stopped = inputMapper.writeValueAsString(new RecallLlmResponse(result.result(), null, List.of(),
                    result.decisionSummary(), null, null));
            return new Invocation(stopped, result, session.elapsedMillis(), session.inputTokens(), session.outputTokens());
        } catch (Exception exception) {
            if (raw != null && trace != null) {
                trace.modelConversionFailed(exception);
            }
            throw new CallException(exception, new Invocation(raw, null, session.elapsedMillis(), session.inputTokens(), session.outputTokens()));
        }
    }

    @Override
    public void close() throws Exception {
        resources.close();
    }

    public record Invocation(String rawResponse, RecallAgentResult result, double responseTimeMillis,
                             Integer inputTokens, Integer outputTokens) {}

    public static class CallException extends RuntimeException {
        private final Invocation invocation;

        public CallException(Exception cause, Invocation invocation) {
            super("Recall model invocation or response conversion failed", cause);
            this.invocation = invocation;
        }

        public Invocation invocation() {
            return invocation;
        }
    }
}
