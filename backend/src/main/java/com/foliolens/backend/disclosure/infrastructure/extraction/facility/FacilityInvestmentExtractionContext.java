package com.foliolens.backend.disclosure.infrastructure.extraction.facility;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;

import java.util.Objects;
import java.util.UUID;

/**
 * 하나의 TABLE ContentBlock에서 시설투자 Evidence를 만들 때 필요한
 * 공시·문서·원문 위치 문맥.
 */
public record FacilityInvestmentExtractionContext(
        UUID disclosureId,
        UUID disclosureDocumentId,
        String receiptNo,
        String documentName,
        DisclosureDocumentRole documentFileRole,
        EventDocumentRole eventDocumentRole,
        UUID sectionId,
        String sectionPath,
        UUID contentBlockId
) {

    public FacilityInvestmentExtractionContext {
        disclosureId = Objects.requireNonNull(
                disclosureId,
                "disclosureId는 필수입니다."
        );
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        receiptNo = requireReceiptNo(receiptNo);
        documentName = requireText(documentName, "documentName");
        documentFileRole = Objects.requireNonNull(
                documentFileRole,
                "documentFileRole은 필수입니다."
        );
        sectionPath = Objects.requireNonNull(
                sectionPath,
                "sectionPath는 null일 수 없습니다."
        ).strip();
        contentBlockId = Objects.requireNonNull(
                contentBlockId,
                "contentBlockId는 필수입니다."
        );
    }

    private static String requireReceiptNo(String value) {
        String normalized = requireText(value, "receiptNo");
        if (!normalized.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }
        return value.strip();
    }
}
