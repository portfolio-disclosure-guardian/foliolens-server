package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;

import java.util.Objects;
import java.util.UUID;

public record XmlParsingPersistenceRow(
        UUID disclosureDocumentId,
        String receiptNo,
        String fileName,
        XmlParsingPersistenceStatus status,
        int deletedSectionCount,
        int deletedBlockCount,
        int savedSectionCount,
        int savedBlockCount,
        long elapsedMillis,
        String errorMessage
) {

    public XmlParsingPersistenceRow {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        Objects.requireNonNull(status, "status는 필수입니다.");

        if (receiptNo == null || receiptNo.isBlank()) {
            throw new IllegalArgumentException(
                    "receiptNo는 필수입니다."
            );
        }

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "fileName은 필수입니다."
            );
        }

        if (elapsedMillis < 0) {
            throw new IllegalArgumentException(
                    "elapsedMillis는 0 이상이어야 합니다."
            );
        }
    }

    public static XmlParsingPersistenceRow success(
            DisclosureDocument document,
            DisclosureParsingPersistenceResult result,
            long elapsedMillis
    ) {
        return new XmlParsingPersistenceRow(
                document.getId(),
                document.getDisclosure().getReceiptNo(),
                document.getFileName(),
                XmlParsingPersistenceStatus.SUCCESS,
                result.deletedSectionCount(),
                result.deletedBlockCount(),
                result.savedSectionCount(),
                result.savedBlockCount(),
                elapsedMillis,
                null
        );
    }

    public static XmlParsingPersistenceRow failed(
            DisclosureDocument document,
            long elapsedMillis,
            String errorMessage
    ) {
        return new XmlParsingPersistenceRow(
                document.getId(),
                document.getDisclosure().getReceiptNo(),
                document.getFileName(),
                XmlParsingPersistenceStatus.FAILED,
                0,
                0,
                0,
                0,
                elapsedMillis,
                normalizeErrorMessage(errorMessage)
        );
    }

    private static String normalizeErrorMessage(String value) {
        if (value == null || value.isBlank()) {
            return "상세 오류 메시지가 없습니다.";
        }

        return value.trim();
    }
}
