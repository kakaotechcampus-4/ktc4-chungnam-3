package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeModelFactoryTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openAiCompatibleTransportMakesForcedStructuredOutputOptIn(boolean forceStructuredOutput) throws Exception {
        var request = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String content = "{\"reasoning\":\"실제 입력에 근거함\",\"violations\":[]}";
            String response = """
                    {"id":"test","object":"chat.completion","created":1,"model":"fake-local-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":21,"completion_tokens":5,"total_tokens":26}}
                    """.formatted(JudgeRunner.MAPPER.writeValueAsString(content));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var model = JudgeModelFactory.create(new JudgeProperties("openai", "fake-local-model", Duration.ofSeconds(10),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "dummy-key", 0.0, forceStructuredOutput),
                new StandardEnvironment())) {
            String systemPrompt = JudgeRunner.systemPrompt("필수 조건");
            var response = model.call(systemPrompt, "actual input");
            assertThat(response.inputTokens()).isEqualTo(21);
            assertThat(response.outputTokens()).isEqualTo(5);
            assertThat(response.durationMillis()).isGreaterThanOrEqualTo(0);
            var payload = JudgeRunner.MAPPER.readTree(request.get());
            assertThat(payload.get("temperature").asDouble()).isZero();
            assertThat(payload.has("response_format")).isEqualTo(forceStructuredOutput);
            if (forceStructuredOutput) {
                assertThat(payload.at("/response_format/type").asText()).isEqualTo("json_schema");
            }
            assertThat(payload.at("/messages/0/content").asText()).isEqualTo(systemPrompt)
                    .contains("JSON 객체 하나", "reasoning", "violations");
            assertThat(payload.at("/messages/1/content").asText()).isEqualTo("actual input");
        } finally {
            server.stop(0);
        }
    }
}
