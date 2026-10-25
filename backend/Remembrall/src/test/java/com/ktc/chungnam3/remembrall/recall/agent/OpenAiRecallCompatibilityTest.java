package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.config.RecallAgentProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallLlmAgentFactory;
import com.ktc.chungnam3.remembrall.recall.evaluation.EvaluationScene;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiRecallCompatibilityTest {
    @Test
    void actualSpringAiRequestContainsOnlyProxyAllowedParametersAndNoTools() throws Exception {
        var mapper = JsonMapper.builder().build();
        var request = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.set(body);
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            Set<String> allowed = Set.of("model", "messages", "max_completion_tokens", "temperature", "top_p", "stop",
                    "stream", "tools", "tool_choice", "response_format", "reasoning_effort");
            boolean accepted = mapper.readTree(body).properties().stream().allMatch(entry -> allowed.contains(entry.getKey()));
            String content = """
                    {"result":"NO_ACTION","proposalType":null,"items":[],"decisionSummary":"출근 시간","title":null,"body":null}
                    """;
            byte[] response = mapper.writeValueAsBytes(accepted ? Map.of("id", "test", "object", "chat.completion",
                    "created", 0, "model", "proxy-model", "choices", List.of(Map.of("index", 0,
                            "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop")),
                    "usage", Map.of("prompt_tokens", 100, "completion_tokens", 20, "total_tokens", 120))
                    : Map.of("error", Map.of("message", "unsupported parameter", "type", "invalid_request_error")));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(accepted ? 200 : 400, response.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(response);
            }
        });
        server.start();
        try {
            var properties = new RecallAgentProperties(RecallAgentProperties.Mode.LLM, RecallAgentProperties.Provider.OPENAI,
                    "proxy-model", Duration.ofSeconds(2), "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key");
            EvaluationScene scene;
            try (var stream = new ClassPathResource("recall/evaluation/scenes.json").getInputStream()) {
                scene = mapper.readValue(stream, EvaluationScene[].class)[1];
            }
            try (var agent = RecallLlmAgentFactory.create(properties,
                    new MockEnvironment().withProperty("recall.agent.tools-enabled", "false"))) {
                var result = agent.invoke(scene.input());
                assertThat(result.result().result()).isEqualTo(com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult.NO_ACTION);
                assertThat(result.inputTokens()).isEqualTo(100);
                assertThat(result.outputTokens()).isEqualTo(20);
            }
            var json = mapper.readTree(request.get());
            assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                    .containsExactlyInAnyOrder("model", "messages");
            assertThat(json.get("model").asText()).isEqualTo("proxy-model");
            assertThat(json.has("stream")).isFalse();
            assertThat(json.get("messages")).hasSize(2);
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
        } finally {
            server.stop(0);
        }
    }
}
