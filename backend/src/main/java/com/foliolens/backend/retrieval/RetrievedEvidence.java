package com.foliolens.backend.retrieval;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;

import java.util.List;

// tableId/headers/rowLabel 등 표 세부 필드는 실제 evidence 검색 구현 시 추가한다.
public record RetrievedEvidence(
        String evidenceId,
        String disclosureId,
        String documentId,
        DisclosureDocumentRole documentRole,
        String sectionId,
        String sectionPath,
        EvidenceBlockType blockType,
        String content,
        double relevanceScore,
        EvidenceStatus status,
        List<RetrievedEvidenceSource> sources) {

    public RetrievedEvidence {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /**
     * 기존 fake fixture가 사용하는 최소 생성 계약을 유지한다.
     */
    public RetrievedEvidence(
            String evidenceId,
            String disclosureId,
            String documentId,
            DisclosureDocumentRole documentRole,
            String sectionId,
            EvidenceBlockType blockType,
            String content,
            double relevanceScore,
            EvidenceStatus status
    ) {
        this(
                evidenceId,
                disclosureId,
                documentId,
                documentRole,
                sectionId,
                sectionId,
                blockType,
                content,
                relevanceScore,
                status,
                List.of()
        );
    }
}
