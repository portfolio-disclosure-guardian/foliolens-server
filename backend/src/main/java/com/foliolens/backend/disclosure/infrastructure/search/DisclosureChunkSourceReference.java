package com.foliolens.backend.disclosure.infrastructure.search;

import java.util.Objects;
import java.util.UUID;

/**
 * 검색된 청크에서 원본 ContentBlock과 XML·TABLE 위치로 돌아가기 위한 참조 정보
 * 검색 청크가 원문의 어디에서 만들어졌는지 알려주는 위치 참조 모델 ...
 *
 */
public record DisclosureChunkSourceReference(
        UUID chunkSourceId, // disclosure_chunk_sources.id
        UUID contentBlockId, // 청크를 만든 실제 원본 블록 ID
        int sourceOrder, // 하나의 청크 안에서 출처가 사용된 순서
        int blockSequenceNo, // 원본 문서 안에서 ContentBlock이 등장한 순서
        int sourceLineStart, // 원본 XML에서 해당 블록이 위치한 행 범위
        int sourceLineEnd, // 원본 XML에서 해당 블록이 위치한 행 범위
        String tableNestingPath, // 중첩 표가 있는 경우 TABLE JSONB 안의 정확한 위치
        Integer tableRowIndexStart, // TABLE 청크를 만들 때 실제로 사용한 표 행 범위
        Integer tableRowIndexEnd, // TABLE 청크를 만들 때 실제로 사용한 표 행 범위
        Integer sourcePageNumber, // PDF 물리 페이지(1부터). null이면 기존 XML/HTML 위치
        boolean textExtractionSuspect // PDF 추출 품질 경고. false도 표·수치 검증을 뜻하지 않음
) {

    public DisclosureChunkSourceReference {
        chunkSourceId = Objects.requireNonNull(
                chunkSourceId,
                "chunkSourceId는 필수입니다."
        );
        contentBlockId = Objects.requireNonNull(
                contentBlockId,
                "contentBlockId는 필수입니다."
        );

        if (sourceOrder < 1) {
            throw new IllegalArgumentException(
                    "sourceOrder는 1 이상이어야 합니다."
            );
        }

        if (blockSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "blockSequenceNo는 1 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);
        if ((sourcePageNumber != null && (sourcePageNumber < 1 || sourceLineStart != -1
                || sourceLineEnd != -1 || tableRowIndexStart != null))
                || (sourcePageNumber == null && textExtractionSuspect)) {
            throw new IllegalArgumentException("PDF 페이지 참조가 올바르지 않습니다.");
        }
        validateTableLocation(
                tableNestingPath,
                tableRowIndexStart,
                tableRowIndexEnd
        );

        tableNestingPath = normalizeOptionalText(tableNestingPath);
    }

    public DisclosureChunkSourceReference(UUID chunkSourceId, UUID contentBlockId, int sourceOrder,
            int blockSequenceNo, int sourceLineStart, int sourceLineEnd, String tableNestingPath,
            Integer tableRowIndexStart, Integer tableRowIndexEnd) {
        this(chunkSourceId, contentBlockId, sourceOrder, blockSequenceNo, sourceLineStart, sourceLineEnd,
                tableNestingPath, tableRowIndexStart, tableRowIndexEnd, null, false);
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

    private static void validateTableLocation(
            String nestingPath,
            Integer rowStart,
            Integer rowEnd
    ) {
        if ((rowStart == null) != (rowEnd == null)) {
            throw new IllegalArgumentException(
                    "표 시작 행과 종료 행은 함께 존재해야 합니다."
            );
        }

        if (nestingPath != null
                && !nestingPath.isBlank()
                && rowStart == null) {
            throw new IllegalArgumentException(
                    "중첩 표 경로가 있으면 표 행 범위도 필요합니다."
            );
        }

        if (rowStart == null) {
            return;
        }

        if (rowStart < 0 || rowEnd < rowStart) {
            throw new IllegalArgumentException(
                    "표 행 범위가 올바르지 않습니다."
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
