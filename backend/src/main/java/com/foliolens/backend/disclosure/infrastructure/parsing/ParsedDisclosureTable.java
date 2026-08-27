package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;
import java.util.Objects;

/**
 * DART XML의 표 하나.
 * TABLE 태그 하나에 대응한다.
 *
 * Table(order=1)
 * ├─ Row(rowIndex=0)
 * │  ├─ Cell(cellIndex=0, HEADER, "구분")
 * │  └─ Cell(cellIndex=1, HEADER, "금액")
 * └─ Row(rowIndex=1)
 *    ├─ Cell(cellIndex=0, DATA, "시설투자")
 *    └─ Cell(cellIndex=1, DATA, "100억원")
 */
public record ParsedDisclosureTable(

        int order, // XML 문서 안에서 표가 등장한 순서.

        int sourceLineStart, // TABLE 시작 태그의 원문 행 번호.
        int sourceLineEnd, // TABLE 종료 태그의 원문 행 번호.

        /*
         * 부모 셀 안에 들어 있는 중첩 표일 때만 사용한다.
         * 최상위 표라면 null이다.
         */
        ParsedDisclosureTableContext parentContext,

        List<ParsedDisclosureTableRow> rows // 표에 포함된 행 목록.

) {

    public ParsedDisclosureTable {
        if (order < 0) {
            throw new IllegalArgumentException(
                    "order는 0 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        rows = List.copyOf(
                Objects.requireNonNull(rows, "rows는 필수입니다.")
        );
    }

    /**
     * 부모 문맥 필드가 추가되기 전 호출부와의 호환을 위한 생성자다.
     * 이 생성자로 만든 표는 최상위 표로 취급한다.
     */
    public ParsedDisclosureTable(
            int order,
            int sourceLineStart,
            int sourceLineEnd,
            List<ParsedDisclosureTableRow> rows
    ) {
        this(
                order,
                sourceLineStart,
                sourceLineEnd,
                null,
                rows
        );
    }

    public boolean hasParentContext() {
        return parentContext != null;
    }

    /**
     * 파싱을 마친 중첩 표에 부모 셀의 인접 문맥을 연결한다.
     */
    public ParsedDisclosureTable withParentContext(
            ParsedDisclosureTableContext context
    ) {
        return new ParsedDisclosureTable(
                order,
                sourceLineStart,
                sourceLineEnd,
                Objects.requireNonNull(
                        context,
                        "context는 필수입니다."
                ),
                rows
        );
    }

    /**
     * 표의 실제 TR 개수를 반환한다.
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * 표에 포함된 실제 TH/TD 개수를 반환한다.
     */
    public int cellCount() {
        return rows.stream()
                .mapToInt(ParsedDisclosureTableRow::cellCount)
                .sum();
    }

    /**
     * 표 안에 중첩 표가 존재하는지 확인한다.
     */
    public boolean hasNestedTables() {
        return rows.stream()
                .flatMap(row -> row.cells().stream())
                .anyMatch(ParsedDisclosureTableCell::hasNestedTables);
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
