package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 여러 HTML 원문을 조사한 한 번의 배치 결과.
 */
public record HtmlStructureProfileBatchResult(
        Instant startedAt,
        Instant completedAt,
        List<HtmlStructureProfileRow> rows
) {

    public HtmlStructureProfileBatchResult {
        startedAt = Objects.requireNonNull(startedAt, "startedAt은 필수입니다.");
        completedAt = Objects.requireNonNull(
                completedAt,
                "completedAt은 필수입니다."
        );
        Objects.requireNonNull(rows, "rows는 필수입니다.");

        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "completedAt은 startedAt보다 빠를 수 없습니다."
            );
        }
        if (rows.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "rows에는 null을 포함할 수 없습니다."
            );
        }
        rows = List.copyOf(rows);
    }

    public int totalCount() {
        return rows.size();
    }

    public long successCount() {
        return countByStatus(HtmlStructureProfileStatus.SUCCESS);
    }

    public long failedCount() {
        return countByStatus(HtmlStructureProfileStatus.FAILED);
    }

    public long elapsedMillis() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }

    private long countByStatus(HtmlStructureProfileStatus status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .count();
    }
}
