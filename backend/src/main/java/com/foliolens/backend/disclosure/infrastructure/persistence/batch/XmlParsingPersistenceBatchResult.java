package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record XmlParsingPersistenceBatchResult(
        Instant startedAt,
        Instant completedAt,
        List<XmlParsingPersistenceRow> rows
) {

    public XmlParsingPersistenceBatchResult {
        Objects.requireNonNull(startedAt, "startedAt은 필수입니다.");
        Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
        rows = List.copyOf(
                Objects.requireNonNull(rows, "rows는 필수입니다.")
        );
    }

    public int totalCount() {
        return rows.size();
    }

    public long successCount() {
        return rows.stream()
                .filter(row ->
                        row.status()
                                == XmlParsingPersistenceStatus.SUCCESS
                )
                .count();
    }

    public long failedCount() {
        return rows.stream()
                .filter(row ->
                        row.status()
                                == XmlParsingPersistenceStatus.FAILED
                )
                .count();
    }

    public int savedSectionCount() {
        return rows.stream()
                .mapToInt(XmlParsingPersistenceRow::savedSectionCount)
                .sum();
    }

    public int savedBlockCount() {
        return rows.stream()
                .mapToInt(XmlParsingPersistenceRow::savedBlockCount)
                .sum();
    }

    public long elapsedMillis() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }
}
