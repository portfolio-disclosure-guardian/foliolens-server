package com.foliolens.backend.retrieval;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetrievedContextResponse(
        @JsonProperty("receipt_no") String receiptNo,
        @JsonProperty("report_name") String reportName,
        @JsonProperty("submitted_at") LocalDate submittedAt,
        String section,
        String content) {

    // ponytail: documentId를 receiptNo로 사용 중. TOOL_CONTRACTS.md의 documentId/receiptNo 분리는 A4 fixture 정렬에서 처리.
    public static RetrievedContextResponse from(RetrievedDocument document) {
        return new RetrievedContextResponse(
                document.documentId(),
                document.reportName(),
                document.submittedAt(),
                document.section(),
                document.content());
    }
}
