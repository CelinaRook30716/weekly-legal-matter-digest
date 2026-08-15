package dev.learning.legaldigest;

import java.time.LocalDate;
import java.util.List;

public final class LegalDigestExample {
    private LegalDigestExample() {
    }

    public static void main(String[] args) throws Exception {
        LocalDate today = LocalDate.now();
        List<LegalMatter> matters = List.of(
                new LegalMatter("MAT-1042", "Aster Labs", today.minusDays(2), false, today.plusDays(3)),
                new LegalMatter("MAT-1048", "Beacon School", today.minusDays(9), true, today.plusDays(6)),
                new LegalMatter("MAT-1051", "Cedar Works", today.minusDays(1), true, today.plusDays(18)));

        WeeklyLegalDigest digest = new WeeklyLegalDigest();
        System.out.println("Weekly legal digest preview:");
        digest.prepare(matters, today).forEach(item -> System.out.printf(
                "- %s | %s | %s | due %s%n",
                item.reference(), item.clientName(), item.action(), item.dueDate()));

        DigestConfig config = DigestConfig.fromEnvironment(System.getenv());
        InfraiCronClient.ScheduledJob job = new InfraiCronClient(config).createWeeklyDigestSchedule();
        System.out.println("Scheduled weekly digest job: " + job.jobId());
    }
}
