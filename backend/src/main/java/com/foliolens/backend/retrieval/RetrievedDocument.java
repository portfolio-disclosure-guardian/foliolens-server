package com.foliolens.backend.retrieval;

import java.time.LocalDate;

public record RetrievedDocument(
        String documentId,
        String companyName,
        String stockCode,
        String disclosureType,
        String reportName,
        LocalDate submittedAt,
        String section,
        String content,
        double relevanceScore) {
}
