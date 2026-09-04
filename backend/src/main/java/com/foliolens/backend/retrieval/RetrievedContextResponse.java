package com.foliolens.backend.retrieval;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetrievedContextResponse(
        @JsonProperty("receipt_no") String receiptNo,
        @JsonProperty("report_name") String reportName,
        @JsonProperty("submitted_at") LocalDate submittedAt,
        String section,
        String content) {

    public static RetrievedContextResponse from(RetrievedDocument document) {
        return new RetrievedContextResponse(
                document.receiptNo(),
                document.reportName(),
                document.submittedAt(),
                document.section(),
                document.content());
    }
}
