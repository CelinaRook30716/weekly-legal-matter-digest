package dev.learning.legaldigest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiCronClient {
    public record ScheduledJob(String jobId) {
    }

    public record Response(int statusCode, Map<String, List<String>> headers, String body) {
        String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }

    @FunctionalInterface
    public interface Transport {
        Response send(HttpRequest request) throws IOException, InterruptedException;
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int statusCode;

        InfraiException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }

    private final DigestConfig config;
    private final Transport transport;
    private final Sleeper sleeper;

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public InfraiCronClient(DigestConfig config) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
        this.config = config;
        this.transport = request -> {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers().map(), response.body());
        };
        this.sleeper = duration -> Thread.sleep(duration.toMillis());
    }

    InfraiCronClient(DigestConfig config, Transport transport, Sleeper sleeper) {
        this.config = config;
        this.transport = transport;
        this.sleeper = sleeper;
    }

    public ScheduledJob createWeeklyDigestSchedule() throws IOException, InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"cron_expr\":\"" + json(config.cronExpression())
                + "\",\"task\":\"" + json(config.digestTaskUrl().toString()) + "\"}";

        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.infraiBaseUri().resolve("/v1/cron/create"))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            Response response = transport.send(request);

            // POST /v1/cron/create: decode {ok,data,error,metadata} before status handling.
            JsonEnvelope.Envelope envelope = JsonEnvelope.parse(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                sleeper.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                String code = String.valueOf(envelope.error().getOrDefault("code", "INFRAI_REQUEST_REJECTED"));
                String message = String.valueOf(envelope.error().getOrDefault("message", "Request rejected"));
                throw new InfraiException(code, message, response.statusCode());
            }
            Object jobId = envelope.data().get("job_id");
            if (jobId == null || jobId.toString().isBlank()) {
                throw new IllegalArgumentException("Successful response did not include job_id");
            }
            return new ScheduledJob(jobId.toString());
        }
        throw new IllegalStateException("Retry loop completed without a result");
    }

    private Duration retryDelay(Response response, int attempt) {
        String retryAfter = response.firstHeader("Retry-After");
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter)));
            } catch (NumberFormatException ignored) {
                // Fall through to exponential delay for non-numeric header values.
            }
        }
        return Duration.ofSeconds(1L << attempt);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
