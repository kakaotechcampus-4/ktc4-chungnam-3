package com.ktc.chungnam3.remembrall.recall.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RecallAgentVersionTest {
    private final ChatClient client = mock(ChatClient.class);

    @Test
    void stubProvidesItsOwnVersion() {
        assertThat(new StubRecallAgent().version()).isEqualTo("stub-v1");
    }

    @Test
    void llmVersionContainsProviderModelPromptVersionAndSha256Prefix() throws Exception {
        var prompt = RecallPrompt.load(new ClassPathResource("recall/prompts/decision.yaml"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(prompt.text().getBytes(StandardCharsets.UTF_8))).substring(0, 6);
        try (var agent = agent("google-genai", "gemini-3.6-flash", prompt)) {
            assertThat(agent.version()).isEqualTo("llm|google-genai|gemini-3.6-flash|prompt-" + prompt.version() + "-" + hash + "|tools-off");
        }
    }

    @Test
    void hashesExactBodyWithoutTrimmingWhitespace() throws Exception {
        try (var original = agent("google-genai", "model", new RecallPrompt("v1", "abc"));
             var withNewline = agent("google-genai", "model", new RecallPrompt("v1", "abc\n"))) {
            assertThat(original.version()).isEqualTo("llm|google-genai|model|prompt-v1-ba7816|tools-off");
            assertThat(withNewline.version()).isNotEqualTo(original.version());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "openai, gemini-3.6-flash, v1, original",
            "google-genai, other-model, v1, original",
            "google-genai, gemini-3.6-flash, v2, original",
            "google-genai, gemini-3.6-flash, v1, changed"
    })
    void changingEachIdentityComponentChangesVersion(String provider, String model, String version, String text)
            throws Exception {
        try (var original = agent("google-genai", "gemini-3.6-flash", new RecallPrompt("v1", "original"));
             var changed = agent(provider, model, new RecallPrompt(version, text))) {
            assertThat(changed.version()).isNotEqualTo(original.version());
            if (text.equals("changed")) {
                assertThat(changed.version()).startsWith("llm|google-genai|gemini-3.6-flash|prompt-v1-");
            }
        }
    }

    @Test
    void yamlLoadsMetadataSeparatelyAndPreservesBodyIncludingTrailingNewline() throws Exception {
        String body = "한국어 판단 기준\n\n두 번째 문단\n";
        var prompt = RecallPrompt.load(resource("version: v1\ntext: |\n  한국어 판단 기준\n\n  두 번째 문단\n"));
        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.text()).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text: body", "version: v1", "version: v1\ntext: ''",
            "version: ''\ntext: body", "version: 1\ntext: body", "version: v1\nversion: v2\ntext: body"})
    void invalidPromptMetadataFailsBeforeAgentCanBeCreated(String yaml) {
        assertThatThrownBy(() -> RecallPrompt.load(resource(yaml))).isInstanceOf(RuntimeException.class);
    }

    private ByteArrayResource resource(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }

    private LlmRecallAgent agent(String provider, String model, RecallPrompt prompt) {
        return new LlmRecallAgent(client, provider, model, prompt, () -> {});
    }
}
