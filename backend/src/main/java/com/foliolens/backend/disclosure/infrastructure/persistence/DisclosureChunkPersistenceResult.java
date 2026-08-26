package com.foliolens.backend.disclosure.infrastructure.persistence;

import java.util.Objects;
import java.util.UUID;

public record DisclosureChunkPersistenceResult(
        UUID disclosureDocumentId,
        int deletedChunkCount,
        int savedChunkCount,
        int savedSourceCount
) {

    public DisclosureChunkPersistenceResult {
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );

        if (deletedChunkCount < 0
                || savedChunkCount < 0
                || savedSourceCount < 0) {
            throw new IllegalArgumentException(
                    "청크 저장 결과의 개수는 0 이상이어야 합니다."
            );
        }
    }
}
