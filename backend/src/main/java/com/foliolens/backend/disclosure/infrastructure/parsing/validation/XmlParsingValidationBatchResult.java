package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record XmlParsingValidationBatchResult(
        Instant startedAt,
        Instant completedAt,
        List<XmlParsingValidationRow> rows
) {

    public XmlParsingValidationBatchResult {
        rows = List.copyOf(rows);
    }

    public int totalCount() {
        return rows.size();
    }

    public long successCount() {
        return count(XmlParsingValidationStatus.SUCCESS);
    }

    public long warningCount() {
        return count(XmlParsingValidationStatus.WARNING);
    }

    public long failedCount() {
        return count(XmlParsingValidationStatus.FAILED);
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }

    public long elapsedMillis() {
        return Duration.between(
                startedAt,
                completedAt
        ).toMillis();
    }

    private long count(XmlParsingValidationStatus status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .count();
    }
}
