package com.foliolens.backend.retrieval;

import java.time.OffsetDateTime;

public record RetrievedDocument(
        String documentId,
        String companyName,
        String stockCode,
        String disclosureType,
        String reportName,
        OffsetDateTime submittedAt,
        String section,
        String content,
        double relevanceScore) {
}
