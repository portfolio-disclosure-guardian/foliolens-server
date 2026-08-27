package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;

/**
 * rowSpan과 colSpan을 펼친 논리적 표의 한 칸.
 * ┌──────────┬───────────┐
 * │          │ 국내       │
 * │   구분    ├───────────┤
 * │          │ 해외       │
 * └──────────┴───────────┘ 을
 *  ->
 * 구분 | 국내
 * 구분 | 해외                로 변경하기 위한 record
 *
 * 하나의 원본 셀이 여러 LogicalTableCell로 확장될 수 있다.
 */
public record LogicalTableCell(
        int rowIndex,
        int columnIndex,

        // 이 논리 칸을 만든 실제 TH 또는 TD
        // 빈 논리 칸이라면 null
        ParsedDisclosureTableCell sourceCell,

        // 원본 셀이 처음 등장한 행과 열
        int originRowIndex,
        int originColumnIndex,

        // 이전 행의 rowSpan으로 내려온 칸인지
        boolean rowSpanContinuation,

        // 같은 행의 colSpan으로 오른쪽에 확장된 칸인지
        boolean colSpanContinuation
) {

    public LogicalTableCell {
        if (rowIndex < 0) {
            throw new IllegalArgumentException(
                    "rowIndex는 0 이상이어야 합니다."
            );
        }

        if (columnIndex < 0) {
            throw new IllegalArgumentException(
                    "columnIndex는 0 이상이어야 합니다."
            );
        }

        if (sourceCell == null) {
            if (originRowIndex != -1 || originColumnIndex != -1) {
                throw new IllegalArgumentException(
                        "빈 논리 셀의 원본 위치는 -1이어야 합니다."
                );
            }

            if (rowSpanContinuation || colSpanContinuation) {
                throw new IllegalArgumentException(
                        "빈 논리 셀은 span 연속 셀일 수 없습니다."
                );
            }
        } else {
            if (originRowIndex < 0 || originColumnIndex < 0) {
                throw new IllegalArgumentException(
                        "원본 셀이 있는 경우 원본 위치가 필요합니다."
                );
            }
        }
    }

    /**
     * 실제 원본 셀이 없는 빈 논리 칸을 만듦
     *
     * 빈 논리 칸은 언제 만들어지나?
     *  실제로 해당 위치를 차지하는 셀이 없을 때만 빈 칸을 만듦
     *  예를 들어 최대 열 개수가 3개인데 어떤 행에는 셀이 2개뿐이라면:
     *  0행: A | B | C
     *  1행: D | E
     *  ->
     *  0행: A | B | C
     *  1행: D | E | 빈 칸 (빈 논리칸 만듦)
     */
    public static LogicalTableCell empty(
            int rowIndex,
            int columnIndex
    ) {
        return new LogicalTableCell(
                rowIndex,
                columnIndex,
                null, // sourceCell을 null로 설정
                -1,
                -1,
                false,
                false
        );
    }

    public boolean isEmpty() {
        return sourceCell == null;
    }

    /**
     * rowSpan·colSpan으로 복제된 칸이 아니라
     * 실제 원본 셀이 시작한 위치인지 반환한다.
     */
    public boolean isOrigin() {
        return !isEmpty() && !rowSpanContinuation && !colSpanContinuation;
    }

    public String text() {
        return isEmpty() ? null : sourceCell.text();
    }

    public ParsedDisclosureTableCellType type() {
        return isEmpty() ? null : sourceCell.type();
    }

    public boolean hasNestedTables() {
        return !isEmpty() && sourceCell.hasNestedTables();
    }
}
