package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LogicalTableGrid 전체를 행 단위 문자열로 변환한 결과.
 */
public record SerializedTable(
        int tableOrder,
        int sourceLineStart,
        int sourceLineEnd,
        int columnCount,
        List<SerializedTableRow> rows
) {

    private static final String ROW_SEPARATOR = "\n";

    public SerializedTable {
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

        validateRows(rows, columnCount);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * 표 전체를 줄바꿈으로 연결한다.
     */
    public String text() {
        return String.join(
                ROW_SEPARATOR,
                rows.stream()
                        .map(SerializedTableRow::text)
                        .toList()
        );
    }

    public int characterCount() {
        return text().length();
    }

    /**
     * 표 맨 앞에서 연속해서 등장하는 HEADER 전용 행을 반환한다.
     *
     * TableChunkGenerator가 큰 표를 여러 청크로 나눌 때
     * 각 청크에 반복할 머리글 후보로 사용한다.
     */
    public List<SerializedTableRow> leadingHeaderRows() {
        List<SerializedTableRow> result = new ArrayList<>();

        for (SerializedTableRow row : rows) {
            if (!row.headerOnly()) {
                break;
            }

            result.add(row);
        }

        return List.copyOf(result);
    }

    private static void validateRows(
            List<SerializedTableRow> rows,
            int columnCount
    ) {
        int previousRowIndex = -1;

        for (SerializedTableRow row : rows) {
            Objects.requireNonNull(
                    row,
                    "rows에는 null이 들어갈 수 없습니다."
            );

            if (row.rowIndex() <= previousRowIndex) {
                throw new IllegalArgumentException(
                        "SerializedTableRow는 rowIndex 오름차순이어야 합니다."
                );
            }

            if (row.columnTexts().size() != columnCount) {
                throw new IllegalArgumentException(
                        "직렬화된 행의 열 개수가 표와 다릅니다."
                                + " expected=" + columnCount
                                + ", actual="
                                + row.columnTexts().size()
                                + ", rowIndex=" + row.rowIndex()
                );
            }

            previousRowIndex = row.rowIndex();
        }
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