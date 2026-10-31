package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.config.RecallAgentProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallLlmAgentFactory;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;

public final class RecallEvaluationMain {
    private RecallEvaluationMain() {}

    public static void main(String[] args) throws Exception {
        var environment = new StandardEnvironment();
        var aliases = new HashMap<String, Object>();
        if (environment.getProperty("RECALL_OPENAI_API_KEY") != null) {
            aliases.put("recall.agent.openai-api-key", environment.getProperty("RECALL_OPENAI_API_KEY"));
        }
        if (environment.getProperty("RECALL_OPENAI_BASE_URL") != null) {
            aliases.put("recall.agent.openai-base-url", environment.getProperty("RECALL_OPENAI_BASE_URL"));
        }
        environment.getPropertySources().addLast(new MapPropertySource("recallEvaluationAliases", aliases));
        if (!environment.getProperty("recall.eval.enabled", Boolean.class, false)) {
            System.out.println("Recall evaluation disabled; set recall.eval.enabled=true to call real models.");
            return;
        }
        String mode = environment.getProperty("recall.eval.mode", "json");
        if (mode.equals("seed-db")) {
            runSeedDatabase(environment);
            return;
        }
        if (!mode.equals("json")) {
            throw new IllegalArgumentException("Unknown recall.eval.mode: " + mode);
        }
        var properties = Binder.get(environment).bindOrCreate("recall.agent", RecallAgentProperties.class);
        int repetitions = environment.getProperty("recall.eval.repetitions", Integer.class, 3);
        if (repetitions < 1) {
            throw new IllegalArgumentException("Evaluation repetitions must be positive");
        }
        var resource = new DefaultResourceLoader().getResource(environment.getProperty("recall.eval.scenes",
                "classpath:recall/evaluation/scenes.json"));
        EvaluationScene[] scenes;
        try (var stream = resource.getInputStream()) {
            scenes = JsonMapper.builder().build().readValue(stream, EvaluationScene[].class);
        }
        var ids = new HashSet<String>();
        for (var scene : scenes) {
            if (!ids.add(scene.id())) {
                throw new IllegalArgumentException("Duplicate scene ID: " + scene.id());
            }
        }
        if (scenes.length == 0) {
            throw new IllegalArgumentException("At least one evaluation scene is required");
        }
        Path output = Path.of(environment.getProperty("recall.eval.output", "build/recall-evaluation/results.jsonl"))
                .toAbsolutePath().normalize();
        if (!output.startsWith(Path.of("build").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Evaluation output must be under build");
        }
        try (var run = EvaluationRun.start(output)) {
            printRun(run);
            var runner = new RecallEvaluationRunner();
            var lines = new ArrayList<RecallEvaluationRunner.Line>();
            String models = environment.getProperty("recall.eval.models", properties.model());
            for (String model : models.split(",")) {
                var selected = new RecallAgentProperties(RecallAgentProperties.Mode.LLM, properties.provider(), model.strip(),
                        properties.timeout(), properties.openaiBaseUrl(), properties.openaiApiKey());
                try (var agent = RecallLlmAgentFactory.create(selected, environment)) {
                    lines.addAll(runner.run(Arrays.asList(scenes), repetitions, selected.provider().name(), selected.model(),
                            RecallEvaluationRunner.EvaluationAgent.from(agent), run));
                }
            }
            runner.printSummary(lines, System.out);
        }
    }

    private static void runSeedDatabase(StandardEnvironment environment) throws Exception {
        int repetitions = environment.getProperty("recall.eval.repetitions", Integer.class, 3);
        if (repetitions < 1) {
            throw new IllegalArgumentException("Evaluation repetitions must be positive");
        }
        var resource = new DefaultResourceLoader().getResource(environment.getProperty("recall.eval.scenes",
                "classpath:recall/evaluation/seed-scenes.json"));
        SeedEvaluationScene[] scenes;
        try (var stream = resource.getInputStream()) {
            scenes = JsonMapper.builder().build().readValue(stream, SeedEvaluationScene[].class);
        }
        var ids = new HashSet<String>();
        for (var scene : scenes) {
            if (scene.id() == null || scene.id().isBlank() || !ids.add(scene.id())) {
                throw new IllegalArgumentException("Missing or duplicate scene ID: " + scene.id());
            }
        }
        scenes = selectSeedScenes(scenes, environment.getProperty("recall.eval.split"));
        if (scenes.length == 0) {
            throw new IllegalArgumentException("At least one evaluation scene is required");
        }
        Path output = Path.of(environment.getProperty("recall.eval.output", "build/recall-evaluation/seed-results.jsonl"))
                .toAbsolutePath().normalize();
        if (!output.startsWith(Path.of("build").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Evaluation output must be under build");
        }
        try (var run = EvaluationRun.start(output)) {
            printRun(run);
            runSeedDatabase(environment, scenes, repetitions, run);
        }
    }

    private static void runSeedDatabase(StandardEnvironment environment, SeedEvaluationScene[] scenes,
                                         int repetitions, EvaluationRun run) throws Exception {
        ConfigurableApplicationContext context;
        try {
            context = new SpringApplicationBuilder(SeedEvaluationConfiguration.class)
                    .web(WebApplicationType.NONE).environment(environment)
                    .properties("spring.config.additional-location=optional:file:./src/main/resources/application.yaml",
                            "spring.config.import=optional:file:./secrets.properties", "spring.main.banner-mode=off")
                    .run();
        } catch (Exception exception) {
            var properties = Binder.get(environment).bindOrCreate("recall.agent", RecallAgentProperties.class);
            var lines = new ArrayList<SeedEvaluationRunner.Line>();
            for (String model : models(environment, properties)) {
                lines.addAll(SeedEvaluationRunner.recordFailure(Arrays.asList(scenes), repetitions,
                        properties.provider().name(), model, SeedEvaluationRunner.Stage.SEED, exception, run));
            }
            SeedEvaluationRunner.printSummary(lines, System.out);
            throw new IllegalStateException("Seed DB context failed; scene failures were recorded", exception);
        }
        try (context) {
            var configured = context.getEnvironment();
            var properties = Binder.get(configured).bindOrCreate("recall.agent", RecallAgentProperties.class);
            var runner = context.getBean(SeedEvaluationRunner.class);
            var lines = new ArrayList<SeedEvaluationRunner.Line>();
            for (String model : models(configured, properties)) {
                var selected = new RecallAgentProperties(RecallAgentProperties.Mode.LLM, properties.provider(), model,
                        properties.timeout(), properties.openaiBaseUrl(), properties.openaiApiKey());
                LlmRecallAgent agent;
                try {
                    agent = RecallLlmAgentFactory.create(selected, configured, context.getBean(RecallToolBinding.class));
                } catch (Exception exception) {
                    lines.addAll(SeedEvaluationRunner.recordFailure(Arrays.asList(scenes), repetitions,
                            selected.provider().name(), model, SeedEvaluationRunner.Stage.MODEL_CALL, exception, run));
                    continue;
                }
                try (agent) {
                    lines.addAll(runner.run(Arrays.asList(scenes), repetitions, selected.provider().name(), model,
                            RecallEvaluationRunner.EvaluationAgent.from(agent), run));
                }
            }
            SeedEvaluationRunner.printSummary(lines, System.out);
        }
    }

    private static void printRun(EvaluationRun run) {
        System.out.println("runId: " + run.runId());
        System.out.println("runStartedAt: " + run.startedAt());
        System.out.println("JSONL: " + run.output());
    }

    static SeedEvaluationScene[] selectSeedScenes(SeedEvaluationScene[] scenes, String split) {
        if (split == null || split.isBlank()) {
            return scenes;
        }
        var selected = Arrays.stream(scenes).filter(scene -> split.equals(scene.split()))
                .toArray(SeedEvaluationScene[]::new);
        if (selected.length == 0) {
            throw new IllegalArgumentException("No evaluation scenes for split: " + split);
        }
        return selected;
    }

    private static List<String> models(Environment environment, RecallAgentProperties properties) {
        return Arrays.stream(environment.getProperty("recall.eval.models", properties.model()).split(","))
                .map(String::strip).toList();
    }
}
