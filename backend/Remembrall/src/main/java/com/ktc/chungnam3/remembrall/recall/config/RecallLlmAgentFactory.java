package com.ktc.chungnam3.remembrall.recall.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallPrompt;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.io.IOException;
import java.util.Locale;

public final class RecallLlmAgentFactory {
    private RecallLlmAgentFactory() {}

    public static LlmRecallAgent create(RecallAgentProperties properties, Environment environment) throws IOException {
        return create(properties, environment, null);
    }

    public static LlmRecallAgent create(RecallAgentProperties properties, Environment environment, RecallToolBinding tools) throws IOException {
        var execution = Binder.get(environment).bindOrCreate("recall.agent", RecallAgentExecutionProperties.class);
        if (execution.toolsEnabled() && tools == null) {
            throw new IllegalArgumentException("Tool-enabled recall requires the server tool binding");
        }
        var prompt = RecallPrompt.load(new ClassPathResource("recall/prompts/decision.yaml"));
        String provider = properties.provider().name().toLowerCase(Locale.ROOT).replace('_', '-');
        if (properties.provider() == RecallAgentProperties.Provider.OPENAI) {
            if (properties.openaiApiKey() == null || properties.openaiApiKey().isBlank()) {
                throw new IllegalArgumentException("Recall OpenAI-compatible API key is required");
            }
            var transport = SpringAiOpenAiHttpClient.builder().timeout(properties.timeout()).build();
            var sdk = new OpenAIClientImpl(ClientOptions.builder().httpClient(transport).apiKey(properties.openaiApiKey())
                    .baseUrl(properties.openaiBaseUrl()).timeout(properties.timeout()).maxRetries(0).build());
            try {
                var options = OpenAiChatOptions.builder().model(properties.model())
                        .temperature(null).frequencyPenalty(null).n(null).store(null).parallelToolCalls(null).build();
                var model = OpenAiChatModel.builder().openAiClient(sdk).openAiClientAsync(sdk.async())
                        .options(options).build();
                return new LlmRecallAgent(ChatClient.builder(model).build(), provider, properties.model(), prompt, sdk::close, execution, tools);
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
            var model = GoogleGenAiChatModel.builder().genAiClient(sdk)
                    .options(GoogleGenAiChatOptions.builder().model(properties.model())
                            .responseMimeType(execution.toolsEnabled() ? null : "application/json").build())
                    .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
            return new LlmRecallAgent(ChatClient.builder(model).build(), provider, properties.model(), prompt, model::destroy, execution, tools);
        } catch (RuntimeException exception) {
            sdk.close();
            throw exception;
        }
    }
}
