package com.foliolens.backend.disclosure.domain.fact;

/**
 * Evidence가 원본 XML과 TABLE JSONB에서 위치한 지점.
 * 원문 행을 확인할 수 없으면 시작·종료 행을 모두 -1로 둔다.
 */
public record DisclosureEvidenceLocation(
        int sourceLineStart,
        int sourceLineEnd,
        String tableNestingPath,
        Integer tableRowIndex,
        Integer tableCellIndex
) {

    public DisclosureEvidenceLocation {
        validateSourceLines(sourceLineStart, sourceLineEnd);
        validateTableIndexes(tableRowIndex, tableCellIndex);
        tableNestingPath = normalizeOptionalText(tableNestingPath);
    }

    public static DisclosureEvidenceLocation unknown() {
        return new DisclosureEvidenceLocation(
                -1,
                -1,
                null,
                null,
                null
        );
    }

    public boolean hasSourceLines() {
        return sourceLineStart >= 0;
    }

    public boolean hasTableLocation() {
        return tableRowIndex != null;
    }

    private static void validateSourceLines(int start, int end) {
        boolean bothUnknown = start == -1 && end == -1;
        boolean bothKnown = start >= 0 && end >= start;

        if (!bothUnknown && !bothKnown) {
            throw new IllegalArgumentException(
                    "원문 행은 모두 -1이거나 0 이상의 유효한 범위여야 합니다."
            );
        }
    }

    private static void validateTableIndexes(
            Integer rowIndex,
            Integer cellIndex
    ) {
        if (rowIndex != null && rowIndex < 0) {
            throw new IllegalArgumentException(
                    "tableRowIndex는 0 이상이어야 합니다."
            );
        }
        if (cellIndex != null && cellIndex < 0) {
            throw new IllegalArgumentException(
                    "tableCellIndex는 0 이상이어야 합니다."
            );
        }
        if (cellIndex != null && rowIndex == null) {
            throw new IllegalArgumentException(
                    "tableCellIndex가 있으면 tableRowIndex도 필요합니다."
            );
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
