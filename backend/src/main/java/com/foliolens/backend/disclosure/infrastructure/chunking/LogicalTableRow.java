package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.List;
import java.util.Objects;

/**
 * rowSpan과 colSpan이 적용된 논리적 표의 한 행
 *
 * 같은 LogicalTableGrid 안의 모든 행은
 * 동일한 cells 크기를 가져야 한다.
 */
public record LogicalTableRow(
        int rowIndex,
        int sourceLineStart,
        int sourceLineEnd,
        List<LogicalTableCell> cells
) {

    public LogicalTableRow {
        if (rowIndex < 0) {
            throw new IllegalArgumentException(
                    "rowIndex는 0 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        cells = List.copyOf(
                Objects.requireNonNull(
                        cells,
                        "cells는 필수입니다."
                )
        );

        validateCellPositions(rowIndex, cells);
    }

    public int columnCount() {
        return cells.size();
    }

    public boolean isEmpty() {
        return cells.stream().allMatch(LogicalTableCell::isEmpty);
    }

    private static void validateCellPositions(
            int rowIndex,
            List<LogicalTableCell> cells
    ) {
        for (int columnIndex = 0;
             columnIndex < cells.size();
             columnIndex++) {

            LogicalTableCell cell = Objects.requireNonNull(
                    cells.get(columnIndex),
                    "cells에는 null이 들어갈 수 없습니다."
            );

            if (cell.rowIndex() != rowIndex) {
                throw new IllegalArgumentException(
                        "LogicalTableCell의 rowIndex가 행과 다릅니다."
                );
            }

            if (cell.columnIndex() != columnIndex) {
                throw new IllegalArgumentException(
                        "LogicalTableCell의 columnIndex가 순서와 다릅니다."
                );
            }
        }
    }

    private static void validateSourceLines(
            int start,
            int end
    ) {
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