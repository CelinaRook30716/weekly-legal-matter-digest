package dev.learning.legaldigest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WeeklyLegalDigest {
    public record DigestItem(String reference, String clientName, String action, LocalDate dueDate) {
    }

    public List<DigestItem> prepare(List<LegalMatter> matters, LocalDate today) {
        LocalDate followUpWindow = today.plusDays(7);
        List<DigestItem> items = new ArrayList<>();

        for (LegalMatter matter : matters) {
            if (!matter.signedDocumentDelivered()) {
                items.add(new DigestItem(
                        matter.reference(), matter.clientName(), "Deliver signed document", matter.nextDeadline()));
            } else if (!matter.nextDeadline().isBefore(today)
                    && !matter.nextDeadline().isAfter(followUpWindow)) {
                items.add(new DigestItem(
                        matter.reference(), matter.clientName(), "Follow up before deadline", matter.nextDeadline()));
            }
        }

        return items.stream()
                .sorted(Comparator.comparing(DigestItem::dueDate).thenComparing(DigestItem::reference))
                .toList();
    }
}
