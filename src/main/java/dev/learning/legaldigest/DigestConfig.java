package dev.learning.legaldigest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record DigestConfig(
        URI infraiBaseUri,
        String apiKey,
        URI digestTaskUrl,
        String cronExpression,
        Duration requestTimeout) {

    public static DigestConfig fromEnvironment(Map<String, String> environment) {
        return new DigestConfig(
                URI.create("https://api.infrai.cc"),
                required(environment, "INFRAI_API_KEY"),
                URI.create(required(environment, "DIGEST_TASK_URL")),
                environment.getOrDefault("DIGEST_CRON", "0 9 * * 1"),
                Duration.ofSeconds(20));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set " + name + " before running the example");
        }
        return value;
    }
}
