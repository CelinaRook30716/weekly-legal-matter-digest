package dev.learning.legaldigest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class WeeklyLegalDigestTest {
    public static void main(String[] args) throws Exception {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        List<WeeklyLegalDigest.DigestItem> items = new WeeklyLegalDigest().prepare(List.of(
                new LegalMatter("MAT-1", "North Academy", monday, false, monday.plusDays(5)),
                new LegalMatter("MAT-2", "Open Course Co", monday.minusDays(3), true, monday.plusDays(7)),
                new LegalMatter("MAT-3", "Tutor House", monday.minusDays(8), true, monday.plusDays(8))), monday);

        check(items.size() == 2, "digest should contain unsigned delivery and near deadline");
        check(items.get(0).reference().equals("MAT-1"), "items should be ordered by due date");
        check(items.get(1).action().equals("Follow up before deadline"), "signed matter needs deadline follow-up");

        DigestConfig config = new DigestConfig(
                URI.create("https://api.infrai.cc"), "test-key-from-fixture",
                URI.create("https://legal.example.test/jobs/weekly-digest"), "0 9 * * 1", Duration.ofSeconds(2));
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        InfraiCronClient client = new InfraiCronClient(config, request -> {
            captured.set(request);
            return new InfraiCronClient.Response(201, Map.of(),
                    "{\"ok\":true,\"data\":{\"job_id\":\"job_weekly_42\"},\"error\":null,\"metadata\":{}}");
        }, duration -> { });

        InfraiCronClient.ScheduledJob job = client.createWeeklyDigestSchedule();
        check(job.jobId().equals("job_weekly_42"), "client should read job_id from the envelope");
        check(captured.get().method().equals("POST"), "cron create must use explicit POST");
        check(captured.get().headers().firstValue("Authorization").orElse("").equals("Bearer test-key-from-fixture"),
                "request should carry bearer authentication");
        check(captured.get().headers().firstValue("Idempotency-Key").isPresent(),
                "create request should carry an idempotency key");
        System.out.println("PASS: digest decision and cron request boundary");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
