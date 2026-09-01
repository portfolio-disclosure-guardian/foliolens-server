package com.foliolens.backend.retrieval;

/**
 * 검색된 근거 청크에서 원본 ContentBlock과 XML·TABLE 위치로 돌아가기
 * 위한 역할 A-B 경계 모델.
 */
public record RetrievedEvidenceSource(
        String chunkSourceId,
        String contentBlockId,
        int sourceOrder,
        int blockSequenceNo,
        int sourceLineStart,
        int sourceLineEnd,
        String tableNestingPath,
        Integer tableRowIndexStart,
        Integer tableRowIndexEnd
) {
}
