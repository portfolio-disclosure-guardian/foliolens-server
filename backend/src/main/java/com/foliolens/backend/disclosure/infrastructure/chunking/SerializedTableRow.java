package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.List;
import java.util.Objects;

/**
 * LogicalTableRow 하나를 검색 가능한 문자열 형태로 변환한 결과.
 *
 * columnTexts에는 논리적 열 위치를 유지하기 위해
 * 내부 빈 문자열도 보존한다.
 */
public record SerializedTableRow(
        int rowIndex,
        int sourceLineStart,
        int sourceLineEnd,

        // 논리적 열 순서에 대응하는 셀 문자열
        List<String> columnTexts,

        // 현재 행에 HEADER 셀이 하나라도 있는지
        boolean hasHeaderCell,

        // 텍스트가 있는 모든 셀이 HEADER인지
        boolean headerOnly
) {

    private static final String CELL_SEPARATOR = " | ";

    public SerializedTableRow {
        if (rowIndex < 0) {
            throw new IllegalArgumentException(
                    "rowIndex는 0 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        columnTexts = List.copyOf(
                Objects.requireNonNull(
                        columnTexts,
                        "columnTexts는 필수입니다."
                )
        );

        for (String columnText : columnTexts) {
            Objects.requireNonNull(
                    columnText,
                    "columnTexts에는 null이 들어갈 수 없습니다."
            );
        }

        if (headerOnly && !hasHeaderCell) {
            throw new IllegalArgumentException(
                    "headerOnly 행에는 HEADER 셀이 있어야 합니다."
            );
        }
    }

    /**
     * 검색할 텍스트가 하나도 없는 행인지 확인한다.
     */
    public boolean isEmpty() {
        return columnTexts.stream().allMatch(String::isBlank);
    }

    /**
     * 열 위치를 유지하면서 한 행의 검색 문자열을 만든다.
     *
     * 중간 빈 열은 보존하지만 마지막의 불필요한 빈 열은 제거한다.
     */
    public String text() {
        int lastMeaningfulIndex = -1;

        for (int index = columnTexts.size() - 1; index >= 0; index--) {

            if (!columnTexts.get(index).isBlank()) {
                lastMeaningfulIndex = index;
                break;
            }
        }

        if (lastMeaningfulIndex < 0) {
            return "";
        }

        return String.join(
                CELL_SEPARATOR,
                columnTexts.subList(0, lastMeaningfulIndex + 1)
        );
    }

    public int characterCount() {
        return text().length();
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