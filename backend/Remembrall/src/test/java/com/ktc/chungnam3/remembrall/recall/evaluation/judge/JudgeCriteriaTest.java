package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeCriteriaTest {
    @TempDir Path directory;

    @Test
    void defaultPointsToCanonicalBackendDocument() {
        assertThat(JudgeCriteria.DEFAULT_LOCATION).isEqualTo("file:../docs/에이전트 평가 기준.md");
    }

    @Test
    void readsCurrentDocumentAndHashesExactBytesWithoutLeakingScoringAnnotations() throws Exception {
        Path path = directory.resolve("criteria.md");
        String text = "## 필수 조건\r\n고정 테스트 조건\r\n## 평가 적용 원칙\r\n고정 원칙\r\n"
                + "## 채점 기준\r\n|점수|라벨|reference|judgeNotes|goodChoices|\r\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes);

        var criteria = JudgeCriteria.load(new DefaultResourceLoader(), path.toUri().toString());

        assertThat(criteria.path()).isEqualTo(path.toRealPath().toString());
        assertThat(criteria.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(criteria.text()).isEqualTo(text);
        assertThat(criteria.promptText()).contains("고정 테스트 조건", "고정 원칙")
                .doesNotContain("채점 기준", "|점수|", "reference", "judgeNotes", "goodChoices");

        Files.writeString(path, text.replace("고정 테스트 조건", "변경된 테스트 조건"));
        var changed = JudgeCriteria.load(new DefaultResourceLoader(), path.toString());
        assertThat(changed.path()).isEqualTo(criteria.path());
        assertThat(changed.sha256()).isNotEqualTo(criteria.sha256());
        assertThat(changed.promptText()).contains("변경된 테스트 조건");
    }

    @Test
    void missingDocumentDoesNotFallBackToAClasspathCopy() {
        assertThatThrownBy(() -> JudgeCriteria.load(new DefaultResourceLoader(), directory.resolve("missing.md").toString()))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void canonicalDocumentHasReadableMarkdownAndExcludesItsScoringSection() throws Exception {
        var criteria = JudgeCriteria.load(new DefaultResourceLoader(), JudgeCriteria.DEFAULT_LOCATION);
        assertThat(criteria.text()).startsWith("# 에이전트 평가 기준").contains("## 채점 기준", "| 1 | BAD |");
        assertThat(criteria.promptText()).contains("## 필수 조건", "## 평가 적용 원칙", "현재 비나 눈")
                .doesNotContain("\\#", "\\-", "\\_", "## 채점 기준", "| 점수 |", "| 1 | BAD |");
    }
}
