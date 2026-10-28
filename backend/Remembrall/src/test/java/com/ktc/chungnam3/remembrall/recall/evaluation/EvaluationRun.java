package com.ktc.chungnam3.remembrall.recall.evaluation;

import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EvaluationRun implements AutoCloseable {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DIRECTORY_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH-mm-ss-SSS", Locale.ROOT).withZone(ZoneId.of("Asia/Seoul"));
    private static final Pattern RUN_DIRECTORY = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(?:-\\d+)?");
    private final UUID runId;
    private final Instant startedAt;
    private final Path output;
    private final BufferedWriter writer;

    private EvaluationRun(UUID runId, Instant startedAt, Path output, BufferedWriter writer) {
        this.runId = runId;
        this.startedAt = startedAt;
        this.output = output;
        this.writer = writer;
    }

    public static EvaluationRun start(Path outputTemplate) throws IOException {
        return start(outputTemplate, Clock.systemUTC());
    }

    static EvaluationRun start(Path outputTemplate, Clock clock) throws IOException {
        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        Path template = outputTemplate.toAbsolutePath().normalize();
        String name = template.getFileName().toString();
        int extensionAt = name.lastIndexOf('.');
        String stem = extensionAt > 0 ? name.substring(0, extensionAt) : name;
        String extension = extensionAt > 0 ? name.substring(extensionAt) : ".jsonl";
        Path output = createRunDirectory(template, startedAt).resolve(stem + extension);
        return new EvaluationRun(runId, startedAt, output, Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
    }

    private static Path createRunDirectory(Path template, Instant startedAt) throws IOException {
        Path root = template.getParent();
        if (root.getFileName() != null && RUN_DIRECTORY.matcher(root.getFileName().toString()).matches()) {
            root = root.getParent();
        }
        Files.createDirectories(root);
        String timestamp = DIRECTORY_TIME.format(startedAt);
        for (int sequence = 1; ; sequence++) {
            Path directory = root.resolve(timestamp + (sequence == 1 ? "" : "-" + sequence));
            try {
                return Files.createDirectory(directory);
            } catch (FileAlreadyExistsException exception) {
                continue;
            }
        }
    }

    public UUID runId() {
        return runId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Path output() {
        return output;
    }

    public void write(Object line) throws IOException {
        writer.write(MAPPER.writeValueAsString(line));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
