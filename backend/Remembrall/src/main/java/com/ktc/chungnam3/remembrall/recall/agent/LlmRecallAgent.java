package com.ktc.chungnam3.remembrall.recall.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
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
import java.util.UUID;

public class LlmRecallAgent implements RecallAgent, AutoCloseable {
    private final ChatClient client;
    private final String systemPrompt;
    private final String version;
    private final AutoCloseable resources;
    private final JsonMapper inputMapper;
    private final BeanOutputConverter<RecallLlmResponse> converter = new BeanOutputConverter<>(RecallLlmResponse.class);

    public LlmRecallAgent(ChatClient client, String provider, String model, RecallPrompt prompt,
                          AutoCloseable resources) {
        this.client = client;
        this.systemPrompt = prompt.text();
        this.version = "llm|" + provider + "|" + model + "|prompt-" + prompt.version() + "-" + promptHash(prompt.text());
        this.resources = resources;
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
        return invoke(input).result();
    }

    public Invocation invoke(RecallAgentInput input) {
        long started = System.nanoTime();
        ChatResponse response = null;
        String raw = null;
        try {
            var prompt = new Prompt(List.of(new SystemMessage(systemPrompt + "\n\n" + converter.getFormat()),
                    new UserMessage(inputMapper.writeValueAsString(input))));
            response = client.prompt(prompt).call().chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("Missing recall model response");
            }
            raw = response.getResult().getOutput().getText();
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Empty recall model response");
            }
            var parsed = converter.convert(raw);
            if (parsed == null) {
                throw new IllegalStateException("Missing structured recall result");
            }
            return invocation(raw, parsed.toAgentResult(), response, started);
        } catch (Exception exception) {
            throw new CallException(exception, invocation(raw, null, response, started));
        }
    }

    private Invocation invocation(String raw, RecallAgentResult result, ChatResponse response, long started) {
        var usage = response == null ? null : response.getMetadata().getUsage();
        if (usage instanceof EmptyUsage) {
            usage = null;
        }
        return new Invocation(raw, result, (System.nanoTime() - started) / 1_000_000.0,
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
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
