package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;
import java.util.Objects;

/**
 * DART XML 표의 셀 하나.
 * <p>
 * TH 또는 TD 태그 하나에 대응한다.
 */
public record ParsedDisclosureTableCell(
        /*
         * 현재 행 안에서 셀이 등장한 순서.
         * 0부터 시작한다.
         * 예:
         * 첫 번째 셀 -> 0
         * 두 번째 셀 -> 1
         */
        int cellIndex,
        ParsedDisclosureTableCellType type, // TH인지 TD인지 구분

        /*
            ┌──────────┬───────────┐
            │          │ 국내 매출   │
            │   구분    ├───────────┤
            │          │ 해외 매출   │
            └──────────┴───────────┘
            '구분' 처럼 셀이 여러개의 행이나 열을 병합하고 있을 수 있기 때문에 필요
         */
        int rowSpan, // 셀이 세로 방향으로 차지하는 행의 개수
        int colSpan, // 셀이 가로 방향으로 차지하는 열의 개수

        /*
         * 셀 안에서 수집한 텍스트
         * 빈 셀 또는 중첩 표만 있는 셀은 null일 수 있다.
         * 내부 줄바꿈은 보존한다.
         */
        String text,

        // XML 파서에서 위치를 알 수 없으면 -1
        int sourceLineStart, // TH 또는 TD 시작 태그의 원문 행 번호.
        int sourceLineEnd, // TH 또는 TD 종료 태그의 원문 행 번호.

        List<ParsedDisclosureTable> nestedTables, // 현재 셀 안에 포함된 중첩 표.
        List<ParsedDisclosureImage> images
) {

    public ParsedDisclosureTableCell {
        if (cellIndex < 0) {
            throw new IllegalArgumentException(
                    "cellIndex는 0 이상이어야 합니다."
            );
        }

        type = Objects.requireNonNull(type, "type은 필수입니다.");

        if (rowSpan < 1) {
            throw new IllegalArgumentException(
                    "rowSpan은 1 이상이어야 합니다."
            );
        }

        if (colSpan < 1) {
            throw new IllegalArgumentException(
                    "colSpan은 1 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        text = normalizeNullableText(text);

        nestedTables = List.copyOf(
                Objects.requireNonNull(
                        nestedTables,
                        "nestedTables는 필수입니다."
                )
        );

        images = List.copyOf(
                Objects.requireNonNull(
                        images,
                        "images는 필수입니다."
                )
        );
    }

    /**
     * 현재 셀에 중첩 표가 있는지 반환한다.
     */
    public boolean hasNestedTables() {
        return !nestedTables.isEmpty();
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        /*
         * Windows와 Unix 개행을 \n으로 통일한다.
         * 표 셀의 내부 줄바꿈은 제거하지 않는다.
         */
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();

        return normalized.isBlank()
                ? null
                : normalized;
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
