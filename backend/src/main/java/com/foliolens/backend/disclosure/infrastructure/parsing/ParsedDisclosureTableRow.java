package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;
import java.util.Objects;

public record ParsedDisclosureTableRow(

        int rowIndex, // 현재 표 안에서 행이 등장한 순서, 0부터 시작한다.

        // 알 수 없으면 -1
        int sourceLineStart, // TR 시작 태그의 원문 행 번호
        int sourceLineEnd, // TR 종료 태그의 원문 행 번호

        List<ParsedDisclosureTableCell> cells // 현재 행에 포함된 TH와 TD 목록.
        // cells가 빈 list 일 수 있는 이유
        // 이론적으로 <TR>에는 셀이 있어야 하지만, 실제 원문에는 비정상적이거나 레이아웃용으로 사용된 빈 행이 있을 수 있음
) {

    public ParsedDisclosureTableRow {
        if (rowIndex < 0) {
            throw new IllegalArgumentException(
                    "rowIndex는 0 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        cells = List.copyOf(
                Objects.requireNonNull(cells, "cells는 필수입니다.")
        );
    }

    /**
     * 현재 행에 포함된 셀 개수를 반환한다.
     * colspan을 적용하기 전의 실제 TH/TD 객체 개수다.
     */
    public int cellCount() {
        return cells.size();
    }

    /**
     * colspan을 적용했을 때 현재 행이 차지하는 논리적 열 개수를 반환
     */
    public int logicalColumnCount() {
        return cells.stream()
                .mapToInt(ParsedDisclosureTableCell::colSpan)
                .sum();
    }

    /**
     * 현재 행에 HEADER 셀이 하나라도 있는지 확인
     */
    public boolean hasHeaderCell() {
        return cells.stream()
                .anyMatch(cell ->
                        cell.type() == ParsedDisclosureTableCellType.HEADER
                );
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
