package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record HtmlParsingValidationBatchResult(
        Instant startedAt, Instant finishedAt, long totalTargetCount,
        List<HtmlParsingValidationRow> rows
) {
    public HtmlParsingValidationBatchResult { rows = List.copyOf(rows); }
    public long successCount() {
        return rows.stream().filter(r -> r.status() == HtmlParsingValidationRow.Status.SUCCESS).count();
    }
    public long failedCount() { return rows.size() - successCount(); }
    public long elapsedMillis() { return Duration.between(startedAt, finishedAt).toMillis(); }
}
