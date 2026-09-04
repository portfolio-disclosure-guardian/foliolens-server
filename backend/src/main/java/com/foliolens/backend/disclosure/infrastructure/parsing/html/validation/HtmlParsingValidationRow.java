package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.infrastructure.parsing.validation.XmlParsingValidationMetrics;
import java.util.UUID;

public record HtmlParsingValidationRow(
        UUID documentId, String receiptNo, String fileName, boolean correction,
        String documentName, XmlParsingValidationMetrics metrics, int relatedLinkCount,
        long elapsedMillis, Status status, String errorMessage
) {
    public enum Status { SUCCESS, FAILED }
}
