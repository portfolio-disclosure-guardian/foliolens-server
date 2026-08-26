package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.List;
import java.util.Objects;

/**
 * rowSpan과 colSpan을 펼친 표 전체 결과.
 */
    public record LogicalTableGrid(
        int tableOrder,
        int sourceLineStart,
        int sourceLineEnd,
        int columnCount,
        List<LogicalTableRow> rows
) {

    public LogicalTableGrid {
        if (tableOrder < 0) {
            throw new IllegalArgumentException(
                    "tableOrder는 0 이상이어야 합니다."
            );
        }

        if (columnCount < 0) {
            throw new IllegalArgumentException(
                    "columnCount는 0 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        rows = List.copyOf(
                Objects.requireNonNull(
                        rows,
                        "rows는 필수입니다."
                )
        );

        for (LogicalTableRow row : rows) {
            Objects.requireNonNull(
                    row,
                    "rows에는 null이 들어갈 수 없습니다."
            );

            if (row.columnCount() != columnCount) {
                throw new IllegalArgumentException(
                        "모든 논리 행의 열 개수는 같아야 합니다."
                                + " expected=" + columnCount
                                + ", actual=" + row.columnCount()
                                + ", rowIndex=" + row.rowIndex()
                );
            }
        }
    }

    public int rowCount() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty() || rows.stream().allMatch(LogicalTableRow::isEmpty);
    }

    private static void validateSourceLines(int start, int end) {
        if (start < -1 || end < -1) {
            throw new IllegalArgumentException(
                    "원문 행 번호는 -1 이상이어야 합니다."
            );
        }

        if (start != -1 && end != -1 && end < start) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }
}