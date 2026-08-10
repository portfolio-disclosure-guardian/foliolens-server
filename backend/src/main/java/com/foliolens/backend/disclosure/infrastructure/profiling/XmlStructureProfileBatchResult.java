package com.foliolens.backend.disclosure.infrastructure.profiling;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 여러 XML 원문 파일을 조사한 한 번의 배치 실행 결과.
 *
 * 성공·실패 건수는 rows에서 계산하여 실제 결과 행과 합계가 달라지지 않게 한다.
 */
public record XmlStructureProfileBatchResult(
        Instant startedAt, // 배치 조사를 시작한 시각
        Instant completedAt, // 배치 조사를 완료한 시각
        List<XmlStructureProfileRow> rows // 파일별 구조 조사 결과 행
) {

    public XmlStructureProfileBatchResult {
        startedAt = Objects.requireNonNull(
                startedAt,
                "startedAt은 필수입니다."
        );
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

    /**
     * 배치에서 실제로 조사한 전체 파일 수.
     */
    public int totalCount() {
        return rows.size();
    }

    /**
     * 구조 조사에 성공한 파일 수.
     */
    public long successCount() {
        return countByStatus(XmlStructureProfileStatus.SUCCESS);
    }

    /**
     * 구조 조사에 실패한 파일 수.
     */
    public long failedCount() {
        return countByStatus(XmlStructureProfileStatus.FAILED);
    }

    /**
     * 배치 전체 실행 시간(ms).
     */
    public long elapsedMillis() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * 실패한 파일이 하나라도 있는지 반환한다.
     */
    public boolean hasFailures() {
        return failedCount() > 0;
    }

    private long countByStatus(XmlStructureProfileStatus status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .count();
    }
}
