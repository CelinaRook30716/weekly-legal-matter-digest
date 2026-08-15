package dev.learning.legaldigest;

import java.time.LocalDate;

public record LegalMatter(
        String reference,
        String clientName,
        LocalDate intakeDate,
        boolean signedDocumentDelivered,
        LocalDate nextDeadline) {
}
