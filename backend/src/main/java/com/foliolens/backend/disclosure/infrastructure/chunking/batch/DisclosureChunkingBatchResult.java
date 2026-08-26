package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 한 번 또는 여러 번 나누어 수행한 청킹 배치의 집계 결과다.
 */
public record DisclosureChunkingBatchResult(
        Instant startedAt,
        Instant completedAt,
        List<DisclosureChunkingBatchRow> rows
) {

    public DisclosureChunkingBatchResult {
        startedAt = Objects.requireNonNull(
                startedAt,
                "startedAt은 필수입니다."
        );
        completedAt = Objects.requireNonNull(
                completedAt,
                "completedAt은 필수입니다."
        );
        rows = List.copyOf(
                Objects.requireNonNull(rows, "rows는 필수입니다.")
        );

        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "completedAt은 startedAt보다 앞설 수 없습니다."
            );
        }
    }

    public int totalCount() {
        return rows.size();
    }

    public long successCount() {
        return rows.stream()
                .filter(row ->
                        row.status()
                                == DisclosureChunkingBatchStatus.SUCCESS
                )
                .count();
    }

    public long failedCount() {
        return rows.stream()
                .filter(row ->
                        row.status()
                                == DisclosureChunkingBatchStatus.FAILED
                )
                .count();
    }

    public int deletedChunkCount() {
        return rows.stream()
                .mapToInt(DisclosureChunkingBatchRow::deletedChunkCount)
                .sum();
    }

    public int savedChunkCount() {
        return rows.stream()
                .mapToInt(DisclosureChunkingBatchRow::savedChunkCount)
                .sum();
    }

    public int savedSourceCount() {
        return rows.stream()
                .mapToInt(DisclosureChunkingBatchRow::savedSourceCount)
                .sum();
    }

    public long elapsedMillis() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }
}
