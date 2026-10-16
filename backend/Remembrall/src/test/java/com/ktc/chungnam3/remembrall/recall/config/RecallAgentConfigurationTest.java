package com.ktc.chungnam3.remembrall.recall.config;

import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RecallAgentConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RecallConfiguration.class);

    @Test
    void defaultsToStubAndReducedCandidateLimitsWithoutModelCredentials() {
        context.run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(RecallAgent.class);
            assertThat(application.getBean(RecallAgent.class)).isInstanceOf(StubRecallAgent.class);
            assertThat(application.getBean(RecallAgent.class).version()).isEqualTo("stub-v1");
            var agent = application.getBean(RecallAgentProperties.class);
            assertThat(agent.provider()).isEqualTo(RecallAgentProperties.Provider.GOOGLE_GENAI);
            assertThat(agent.timeout()).isEqualTo(Duration.ofSeconds(30));
            var candidates = application.getBean(RecallCandidateProperties.class);
            assertThat(candidates.nearbyPersonalLimit()).isEqualTo(10);
            assertThat(candidates.nearbyPublicLimit()).isEqualTo(10);
            assertThat(candidates.semanticLimit()).isEqualTo(5);
            assertThat(candidates.maxSummaryLength()).isEqualTo(200);
        });
    }

    @Test
    void selectingLlmCreatesIsolatedOpenAiCompatibleAgent() {
        context.withPropertyValues("recall.agent.mode=llm", "recall.agent.provider=openai",
                "recall.agent.model=test-model", "recall.agent.openai-api-key=test-key",
                "recall.agent.openai-base-url=http://127.0.0.1:1/v1").run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(RecallAgent.class);
            assertThat(application.getBean(RecallAgent.class)).isInstanceOf(LlmRecallAgent.class);
            assertThat(application.getBean(RecallAgent.class).version()).matches("llm\\|openai\\|test-model\\|prompt-v1-[0-9a-f]{6}");
            assertThat(application.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class)).isEmpty();
        });
    }

    @Test
    void explicitGoogleProviderCreatesLlmAgentWithoutCallingModelAtStartup() {
        context.withPropertyValues("recall.agent.mode=llm", "recall.agent.provider=google-genai",
                "spring.ai.google.genai.api-key=test-key").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(RecallAgent.class)).isInstanceOf(LlmRecallAgent.class);
            assertThat(application.getBean(RecallAgent.class).version())
                    .matches("llm\\|google-genai\\|gemini-3\\.6-flash\\|prompt-v1-[0-9a-f]{6}");
        });
    }
}
