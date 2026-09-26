package dev.bryrich.credapp.document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** A document as its list shows it: everything except the file. */
public record DocumentSummary(long id, DocumentType type, String title, String fileName, String contentType,
                              long sizeBytes, LocalDate expirationDate, String uploadedBy, Instant createdAt) {

    /** "240 KB", "3.1 MB". */
    public String size() {
        if (sizeBytes < 1024 * 1024) {
            return Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    public LocalDate uploadedOn() {
        return createdAt.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public boolean expired() {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }

    /** Within 30 days of expiring. */
    public boolean expiringSoon() {
        return expirationDate != null && !expired()
                && ChronoUnit.DAYS.between(LocalDate.now(), expirationDate) <= 30;
    }
}
