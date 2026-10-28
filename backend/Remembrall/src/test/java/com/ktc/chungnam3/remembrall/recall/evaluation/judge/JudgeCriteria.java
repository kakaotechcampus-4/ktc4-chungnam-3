package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

record JudgeCriteria(String path, String sha256, String text) {
    static final String DEFAULT_LOCATION = "file:../docs/에이전트 평가 기준.md";
    private static final Pattern SCORING_HEADING = Pattern.compile("(?m)^##[\\t ]+채점 기준[\\t ]*\\r?$");

    static JudgeCriteria load(ResourceLoader resources, String location) throws IOException {
        var resource = resources.getResource(location.startsWith("classpath:") || location.startsWith("file:")
                ? location : Path.of(location).toUri().toString());
        String path = resource.isFile() ? resource.getFile().toPath().toRealPath().toString() : location;
        try (var stream = resource.getInputStream()) {
            return fromBytes(path, stream.readAllBytes());
        }
    }

    static JudgeCriteria fromText(String path, String text) {
        return fromBytes(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static JudgeCriteria fromBytes(String path, byte[] bytes) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new JudgeCriteria(path, hash, new String(bytes, StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    String promptText() {
        var scoring = SCORING_HEADING.matcher(text);
        return scoring.find() ? text.substring(0, scoring.start()).stripTrailing() : text;
    }
}
