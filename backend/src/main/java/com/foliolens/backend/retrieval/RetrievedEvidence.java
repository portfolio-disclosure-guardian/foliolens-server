package com.foliolens.backend.retrieval;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;

// tableId/headers/rowLabel 등 표 세부 필드는 실제 evidence 검색 구현 시 추가한다.
public record RetrievedEvidence(
        String evidenceId,
        String disclosureId,
        String documentId,
        DisclosureDocumentRole documentRole,
        String sectionId,
        EvidenceBlockType blockType,
        String content,
        double relevanceScore,
        EvidenceStatus status) {
}
