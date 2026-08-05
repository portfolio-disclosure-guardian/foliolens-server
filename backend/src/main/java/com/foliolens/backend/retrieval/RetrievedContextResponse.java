package com.foliolens.backend.retrieval;

import java.time.LocalDateTime;

public record RetrievedContextResponse(Long receiptNo,
        String reportName,
        LocalDateTime submittedAt,
        String section,
        String content) {

}
