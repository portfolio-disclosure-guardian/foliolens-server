package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;

import java.util.UUID;

public record XmlParsingValidationRow(
        UUID disclosureDocumentId,
        String receiptNo,
        String sourceGroup,
        String reportName,
        String fileName,
        String documentRole,
        long fileSizeBytes,

        String parsedDocumentName,
        int sectionCount,
        int maxSectionLevel,
        int totalBlockCount,
        int headingCount,
        int paragraphCount,
        int pageBreakCount,
        int tableCount,
        int nestedTableCount,
        int tableRowCount,
        int tableCellCount,
        int imageCount,
        long textCharacterCount,

        long elapsedMillis,
        XmlParsingValidationStatus status,

        String warningMessage,
        String errorType,
        Integer errorLine,
        Integer errorColumn,
        String errorMessage
) {

    public static XmlParsingValidationRow success(
            DisclosureDocument document,
            ParsedDisclosureDocument parsed,
            XmlParsingValidationMetrics metrics,
            long elapsedMillis
    ) {
        String warningMessage = findWarning(parsed, metrics);

        XmlParsingValidationStatus status =
                warningMessage == null
                        ? XmlParsingValidationStatus.SUCCESS
                        : XmlParsingValidationStatus.WARNING;

        Disclosure disclosure = document.getDisclosure();

        return new XmlParsingValidationRow(
                document.getId(),
                disclosure.getReceiptNo(),
                disclosure.getSourceGroup().getValue(),
                disclosure.getReportName(),
                document.getFileName(),
                document.getDocumentRole().name(),
                document.getFileSizeBytes(),

                parsed.documentName(),
                metrics.sectionCount(),
                metrics.maxSectionLevel(),
                metrics.totalBlockCount(),
                metrics.headingCount(),
                metrics.paragraphCount(),
                metrics.pageBreakCount(),
                metrics.tableCount(),
                metrics.nestedTableCount(),
                metrics.tableRowCount(),
                metrics.tableCellCount(),
                metrics.imageCount(),
                metrics.textCharacterCount(),

                elapsedMillis,
                status,

                warningMessage,
                null,
                null,
                null,
                null
        );
    }

    private static String findWarning(
            ParsedDisclosureDocument document,
            XmlParsingValidationMetrics metrics
    ) {
        if (document.documentName() == null) {
            return "DOCUMENT-NAME이 없습니다.";
        }

        if (metrics.totalBlockCount() == 0) {
            return "구조화된 본문 블록이 없습니다.";
        }

        if (metrics.textCharacterCount() == 0) {
            return "구조화된 텍스트가 없습니다.";
        }

        return null;
    }

    public static XmlParsingValidationRow failed(
            DisclosureDocument document,
            long elapsedMillis,
            String errorType,
            Integer errorLine,
            Integer errorColumn,
            String errorMessage
    ) {
        Disclosure disclosure = document.getDisclosure();

        return new XmlParsingValidationRow(
                document.getId(),
                disclosure.getReceiptNo(),
                disclosure.getSourceGroup().getValue(),
                disclosure.getReportName(),
                document.getFileName(),
                document.getDocumentRole().name(),
                document.getFileSizeBytes(),

                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,

                elapsedMillis,
                XmlParsingValidationStatus.FAILED,

                null,
                errorType,
                errorLine,
                errorColumn,
                errorMessage
        );
    }
}
