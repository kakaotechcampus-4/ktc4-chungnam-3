package com.ktc.chungnam3.remembrall.recall.agent;

import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.Map;

public record RecallPrompt(String version, String text) {
    public RecallPrompt {
        if (version == null || version.isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Recall prompt version and text are required");
        }
    }

    public static RecallPrompt load(Resource resource) throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var stream = resource.getInputStream()) {
            Object document = new Yaml(new SafeConstructor(options)).load(stream);
            if (!(document instanceof Map<?, ?> fields)
                    || !(fields.get("version") instanceof String version)
                    || !(fields.get("text") instanceof String text)) {
                throw new IllegalArgumentException("Recall prompt must contain version and text strings");
            }
            return new RecallPrompt(version, text);
        }
    }
}
