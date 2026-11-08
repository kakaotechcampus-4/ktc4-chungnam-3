package com.ktc.chungnam3.remembrall.bruno;

import com.ktc.chungnam3.remembrall.RemembrallApplication;
import org.springframework.boot.SpringApplication;

/** Deliberately lives in src/test: no test controls are shipped in the application JAR. */
public final class BrunoHarness {
    public static void main(String[] args) throws java.io.IOException {
        validateEnvironment(System.getenv(), args);
        String pidFile = System.getenv("BRUNO_HARNESS_PIDFILE");
        if (pidFile != null) java.nio.file.Files.writeString(java.nio.file.Path.of(pidFile), Long.toString(ProcessHandle.current().pid()));
        SpringApplication application = new SpringApplication(RemembrallApplication.class, BrunoHarnessConfiguration.class);
        application.setAdditionalProfiles("bruno-harness");
        application.run(args);
    }

    // Run before Spring/Flyway can initialize: refuse an existing/production DB URL.
    static void validateEnvironment(java.util.Map<String, String> environment, String[] args) {
        String url = environment.getOrDefault("SPRING_DATASOURCE_URL", "");
        String token = environment.getOrDefault("BRUNO_HARNESS_TOKEN", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/bruno_regression") || token.length() < 32) {
            throw new IllegalStateException("Use test/harness/run.sh with a disposable localhost bruno_regression database");
        }
        for (String argument : args) {
            if (!argument.matches("--server\\.port=[0-9]+")) {
                throw new IllegalArgumentException("Harness only accepts the runner's server port argument");
            }
        }
    }
}
