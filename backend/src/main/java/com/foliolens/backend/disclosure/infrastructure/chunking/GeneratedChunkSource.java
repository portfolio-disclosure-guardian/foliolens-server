package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.Objects;
import java.util.UUID;

public record GeneratedChunkSource(
        UUID contentBlockId,
        int blockSequenceNo,
        int sourceLineStart,
        int sourceLineEnd,

        // TABLE 청크에서만 사용한다.
        String tableNestingPath,
        Integer tableRowIndexStart,
        Integer tableRowIndexEnd
) {

    public GeneratedChunkSource {
        contentBlockId = Objects.requireNonNull(
                contentBlockId,
                "contentBlockId는 필수입니다."
        );

        if (blockSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "blockSequenceNo는 1 이상이어야 합니다."
            );
        }

        validateSourceLines(
                sourceLineStart,
                sourceLineEnd
        );

        tableNestingPath = normalizeNullable(tableNestingPath);

        validateTableRows(
                tableRowIndexStart,
                tableRowIndexEnd
        );
    }

    /**
     * HEADING·PARAGRAPH·IMAGE 출처를 만든다.
     */
    public static GeneratedChunkSource block(
            UUID contentBlockId,
            int blockSequenceNo,
            int sourceLineStart,
            int sourceLineEnd
    ) {
        return new GeneratedChunkSource(
                contentBlockId,
                blockSequenceNo,
                sourceLineStart,
                sourceLineEnd,
                null,
                null,
                null
        );
    }

    /**
     * TABLE의 특정 행 범위 출처를 만든다.
     */
    public static GeneratedChunkSource tableRows(
            UUID contentBlockId,
            int blockSequenceNo,
            int sourceLineStart,
            int sourceLineEnd,
            String tableNestingPath,
            int rowIndexStart,
            int rowIndexEnd
    ) {
        return new GeneratedChunkSource(
                contentBlockId,
                blockSequenceNo,
                sourceLineStart,
                sourceLineEnd,
                tableNestingPath,
                rowIndexStart,
                rowIndexEnd
        );
    }

    public boolean isTableSource() {
        return tableRowIndexStart != null;
    }

    private static void validateSourceLines(
            int start,
            int end
    ) {
        if (start < -1 || end < -1) {
            throw new IllegalArgumentException(
                    "원문 행은 -1 이상이어야 합니다."
            );
        }

        if (
                start != -1
                        && end != -1
                        && end < start
        ) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }

    private static void validateTableRows(
            Integer start,
            Integer end
    ) {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException(
                    "표 시작 행과 종료 행은 함께 존재해야 합니다."
            );
        }

        if (start == null) {
            return;
        }

        if (start < 0 || end < 0) {
            throw new IllegalArgumentException(
                    "표 행 인덱스는 0 이상이어야 합니다."
            );
        }

        if (end < start) {
            throw new IllegalArgumentException(
                    "표 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}
