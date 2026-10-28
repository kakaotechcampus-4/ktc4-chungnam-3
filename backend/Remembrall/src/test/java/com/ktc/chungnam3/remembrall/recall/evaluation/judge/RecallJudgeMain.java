package com.ktc.chungnam3.remembrall.recall.evaluation.judge;

import com.ktc.chungnam3.remembrall.recall.evaluation.SeedEvaluationScene;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

public final class RecallJudgeMain {
    private RecallJudgeMain() {}

    public static void main(String[] args) throws Exception {
        var environment = new StandardEnvironment();
        var resources = new DefaultResourceLoader();
        var secrets = resources.getResource("file:./secrets.properties");
        if (secrets.exists()) {
            environment.getPropertySources().addLast(new PropertiesPropertySource("judgeSecrets",
                    PropertiesLoaderUtils.loadProperties(secrets)));
        }
        var aliases = new HashMap<String, Object>();
        for (String suffix : new String[]{"PROVIDER", "MODEL", "OPENAI_BASE_URL", "OPENAI_API_KEY", "TEMPERATURE",
                "FORCE_STRUCTURED_OUTPUT"}) {
            String value = environment.getProperty("RECALL_JUDGE_" + suffix);
            if (value != null) {
                String property = switch (suffix) {
                    case "OPENAI_BASE_URL" -> "openai-base-url";
                    case "OPENAI_API_KEY" -> "openai-api-key";
                    case "FORCE_STRUCTURED_OUTPUT" -> "force-structured-output";
                    default -> suffix.toLowerCase(java.util.Locale.ROOT);
                };
                aliases.put("recall.judge." + property, value);
            }
        }
        environment.getPropertySources().addAfter("systemProperties", new MapPropertySource("judgeAliases", aliases));
        Path input = Path.of(environment.getRequiredProperty("recall.judge.input")).toAbsolutePath().normalize();
        String human = environment.getProperty("recall.judge.human-labels");
        if (human != null && !human.isBlank()) {
            String report = HumanLabelCsv.compare(input, Path.of(human)).markdown();
            System.out.println(report);
            return;
        }
        String scenesLocation = environment.getRequiredProperty("recall.judge.scenes");
        var sceneResource = resources.getResource(scenesLocation.startsWith("classpath:") || scenesLocation.startsWith("file:")
                ? scenesLocation : Path.of(scenesLocation).toUri().toString());
        SeedEvaluationScene[] scenes;
        try (var stream = sceneResource.getInputStream()) {
            scenes = JudgeRunner.MAPPER.readValue(stream, SeedEvaluationScene[].class);
        }
        String conditionsLocation = environment.getProperty("recall.judge.conditions",
                JudgeCriteria.DEFAULT_LOCATION);
        var conditions = JudgeCriteria.load(resources, conditionsLocation);
        var properties = Binder.get(environment).bindOrCreate("recall.judge", JudgeProperties.class);
        properties.validate();
        try (var model = JudgeModelFactory.create(properties, environment)) {
            var run = new JudgeRunner(model, properties.provider(), properties.model(), conditions)
                    .run(input, Arrays.asList(scenes));
            System.out.println("Judged JSONL: " + run.judgedJsonl());
            System.out.println("Report: " + run.report());
            System.out.println("Human CSV: " + run.humanCsv());
            System.out.println("Criteria: " + conditions.path());
            System.out.println("Criteria SHA-256: " + conditions.sha256());
            System.out.println(Files.readString(run.report(), StandardCharsets.UTF_8));
        }
    }
}
