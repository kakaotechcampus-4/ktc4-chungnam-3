package com.ktc.chungnam3.remembrall.recall.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunTest {
    @TempDir Path directory;

    @Test
    void simultaneousStartTimesCreateSeparateSeoulTimestampDirectoriesAndPreserveTheConfiguredBaseFile() throws Exception {
        var startedAt = Instant.parse("2026-10-22T18:20:30.123456Z");
        var clock = Clock.fixed(startedAt, ZoneOffset.UTC);
        Path template = directory.resolve("results.jsonl");
        Files.writeString(template, "previous results\n");
        Path firstPath;
        Path secondPath;
        try (var first = EvaluationRun.start(template, clock);
             var second = EvaluationRun.start(template, clock)) {
            firstPath = first.output();
            secondPath = second.output();
            assertThat(first.runId()).isNotEqualTo(second.runId());
            assertThat(first.startedAt()).isEqualTo(startedAt);
            assertThat(second.startedAt()).isEqualTo(startedAt);
            assertThat(firstPath).isNotEqualTo(secondPath);
            assertThat(firstPath).isEqualTo(directory.resolve("2026-10-23_03-20-30-123/results.jsonl"));
            assertThat(secondPath).isEqualTo(directory.resolve("2026-10-23_03-20-30-123-2/results.jsonl"));
            first.write("first run");
            second.write("second run");
        }
        assertThat(Files.readString(firstPath)).isEqualTo("\"first run\"" + System.lineSeparator());
        assertThat(Files.readString(secondPath)).isEqualTo("\"second run\"" + System.lineSeparator());
        assertThat(Files.readString(template)).isEqualTo("previous results\n");
    }

    @Test
    void createsParentDirectoriesAndUsesJsonlWhenTheTemplateHasNoExtension() throws Exception {
        try (var run = EvaluationRun.start(directory.resolve("nested/evaluation"))) {
            assertThat(run.output()).exists();
            assertThat(run.output().getFileName().toString()).isEqualTo("evaluation.jsonl");
            assertThat(run.output().getParent().getParent()).isEqualTo(directory.resolve("nested"));
            assertThat(run.output().getParent().getFileName().toString())
                    .matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(?:-\\d+)?");
        }
    }

    @Test
    void outputInsideAPreviousRunCreatesASiblingRunRatherThanNestedTimestampDirectories() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-22T18:20:30.123Z"), ZoneOffset.UTC);
        try (var first = EvaluationRun.start(directory.resolve("results.jsonl"), clock);
             var second = EvaluationRun.start(first.output(), clock)) {
            first.write("original");
            second.write("new");

            assertThat(second.output().getParent().getParent()).isEqualTo(directory);
            assertThat(second.output()).isEqualTo(directory.resolve("2026-10-23_03-20-30-123-2/results.jsonl"));
            assertThat(Files.readString(first.output())).contains("original");
        }
    }

    @Test
    void anExistingFileWithTheTimestampNameIsNotOverwritten() throws Exception {
        Path existing = directory.resolve("2026-10-23_03-20-30-123");
        Files.writeString(existing, "keep me");
        var clock = Clock.fixed(Instant.parse("2026-10-22T18:20:30.123Z"), ZoneOffset.UTC);

        try (var run = EvaluationRun.start(directory.resolve("results.jsonl"), clock)) {
            assertThat(run.output()).isEqualTo(directory.resolve("2026-10-23_03-20-30-123-2/results.jsonl"));
            assertThat(Files.readString(existing)).isEqualTo("keep me");
        }
    }

    @Test
    void concurrentExecutionsAtTheSameInstantCreateUniqueRunDirectories() throws Exception {
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        var clock = Clock.fixed(Instant.parse("2026-10-22T18:20:30.123Z"), ZoneOffset.UTC);
        var outputs = new ArrayList<Path>();
        try (var threads = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<Path>>();
            for (int index = 0; index < 4; index++) {
                futures.add(threads.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    try (var run = EvaluationRun.start(directory.resolve("results.jsonl"), clock)) {
                        run.write(run.runId());
                        return run.output();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                outputs.add(future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            start.countDown();
        }

        assertThat(outputs).hasSize(4).doesNotHaveDuplicates().allSatisfy(path -> {
            assertThat(path).exists();
            assertThat(path.getFileName().toString()).isEqualTo("results.jsonl");
            assertThat(path.getParent().getParent()).isEqualTo(directory);
        });
        assertThat(outputs.stream().map(path -> path.getParent().getFileName().toString()).toList())
                .containsExactlyInAnyOrderElementsOf(List.of("2026-10-23_03-20-30-123", "2026-10-23_03-20-30-123-2",
                        "2026-10-23_03-20-30-123-3", "2026-10-23_03-20-30-123-4"));
    }
}
