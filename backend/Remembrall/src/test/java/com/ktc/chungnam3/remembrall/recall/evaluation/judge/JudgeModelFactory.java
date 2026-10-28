package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.util.List;

public final class JudgeModelFactory {
    private JudgeModelFactory() {}

    public static JudgeModel create(JudgeProperties properties, Environment environment) {
        properties.validate();
        String schema = properties.forceStructuredOutput()
                ? new BeanOutputConverter<>(JudgeRunner.Decision.class).getJsonSchema() : null;
        if (properties.provider().equals("openai")) {
            var transport = SpringAiOpenAiHttpClient.builder().timeout(properties.timeout()).build();
            var sdk = new OpenAIClientImpl(ClientOptions.builder().httpClient(transport)
                    .apiKey(properties.openaiApiKey()).baseUrl(properties.openaiBaseUrl())
                    .timeout(properties.timeout()).maxRetries(0).build());
            try {
                var options = OpenAiChatOptions.builder().model(properties.model())
                        .temperature(properties.temperature()).frequencyPenalty(null).n(null)
                        .store(null).parallelToolCalls(null);
                if (properties.forceStructuredOutput()) {
                    options.outputSchema(schema);
                }
                var model = OpenAiChatModel.builder().openAiClient(sdk).openAiClientAsync(sdk.async())
                        .options(options.build()).build();
                return wrap(ChatClient.builder(model).build(), sdk::close);
            } catch (RuntimeException exception) {
                sdk.close();
                throw exception;
            }
        }
        var builder = Client.builder().httpOptions(HttpOptions.builder()
                .timeout(Math.toIntExact(properties.timeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build());
        if (environment.getProperty("spring.ai.google.genai.vertex-ai", Boolean.class, false)) {
            builder.vertexAI(true).project(environment.getProperty("spring.ai.google.genai.project-id"))
                    .location(environment.getProperty("spring.ai.google.genai.location", "us-central1"));
        } else {
            builder.apiKey(environment.getProperty("spring.ai.google.genai.api-key",
                    environment.getProperty("gemini.api.key", "")));
        }
        Client sdk = builder.build();
        try {
            var options = GoogleGenAiChatOptions.builder().model(properties.model()).temperature(properties.temperature());
            if (properties.forceStructuredOutput()) {
                options.responseMimeType("application/json").responseSchema(schema);
            }
            var model = GoogleGenAiChatModel.builder().genAiClient(sdk)
                    .options(options.build())
                    .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
            return wrap(ChatClient.builder(model).build(), model::destroy);
        } catch (RuntimeException exception) {
            sdk.close();
            throw exception;
        }
    }

    static JudgeModel wrap(ChatClient client, AutoCloseable resources) {
        return new JudgeModel() {
            @Override
            public Response call(String systemPrompt, String userMessage) {
                long started = System.nanoTime();
                var response = client.prompt(new Prompt(List.of(new SystemMessage(systemPrompt),
                        new UserMessage(userMessage)))).call().chatResponse();
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    throw new IllegalStateException("Missing Judge response");
                }
                var usage = response.getMetadata().getUsage();
                if (usage instanceof EmptyUsage) {
                    usage = null;
                }
                return new Response(response.getResult().getOutput().getText(),
                        (System.nanoTime() - started) / 1_000_000.0,
                        usage == null ? null : usage.getPromptTokens(),
                        usage == null ? null : usage.getCompletionTokens());
            }

            @Override
            public void close() throws Exception {
                resources.close();
            }
        };
    }
}
