package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

public record DisclosureFactPersistenceResult(
        int deletedFactCount,
        int deletedEvidenceCount,
        int savedFactCount,
        int savedEvidenceCount,
        int savedLinkCount
) {

    public DisclosureFactPersistenceResult {
        if (deletedFactCount < 0
                || deletedEvidenceCount < 0
                || savedFactCount < 0
                || savedEvidenceCount < 0
                || savedLinkCount < 0) {
            throw new IllegalArgumentException("저장 결과 건수는 0 이상이어야 합니다.");
        }
    }
}
