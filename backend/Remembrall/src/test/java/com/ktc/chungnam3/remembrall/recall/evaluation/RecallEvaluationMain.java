package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.recall.config.RecallAgentProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallLlmAgentFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;

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
        var runner = new RecallEvaluationRunner();
        var lines = new ArrayList<RecallEvaluationRunner.Line>();
        String models = environment.getProperty("recall.eval.models", properties.model());
        for (String model : models.split(",")) {
            var selected = new RecallAgentProperties(RecallAgentProperties.Mode.LLM, properties.provider(), model.strip(),
                    properties.timeout(), properties.openaiBaseUrl(), properties.openaiApiKey());
            try (var agent = RecallLlmAgentFactory.create(selected, environment)) {
                lines.addAll(runner.run(Arrays.asList(scenes), repetitions, selected.provider().name(), selected.model(),
                        RecallEvaluationRunner.EvaluationAgent.from(agent), output));
            }
        }
        runner.printSummary(lines, System.out);
        System.out.println("JSONL: " + output);
    }
}
