package com.foliolens.backend.disclosure.domain.fact;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;

import java.util.Objects;
import java.util.UUID;

/**
 * 하나의 Fact를 판단할 때 실제 사용한 원문 문장·표 행·표 셀 근거.
 * 검색된 청크 후보 자체가 아니라 원본 ContentBlock 안에서 다시 특정한
 * 재현 가능한 근거다.
 */
public record DisclosureEvidence(
        UUID evidenceId,
        UUID disclosureId,
        UUID disclosureDocumentId,
        String receiptNo,
        String documentName,
        DisclosureDocumentRole documentFileRole,
        EventDocumentRole eventDocumentRole,
        UUID sectionId,
        String sectionPath,
        UUID contentBlockId,
        EvidenceBlockType blockType,
        String tableIndexOrName,
        DisclosureEvidenceLocation location,
        DisclosureEvidenceValue value,
        EvidenceStatus status
) {

    public DisclosureEvidence {
        evidenceId = requireId(evidenceId, "evidenceId");
        disclosureId = requireId(disclosureId, "disclosureId");
        disclosureDocumentId = requireId(
                disclosureDocumentId,
                "disclosureDocumentId"
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
        blockType = Objects.requireNonNull(
                blockType,
                "blockType은 필수입니다."
        );
        tableIndexOrName = normalizeOptionalText(tableIndexOrName);
        location = Objects.requireNonNull(
                location,
                "location은 필수입니다."
        );
        value = Objects.requireNonNull(value, "value는 필수입니다.");
        status = Objects.requireNonNull(status, "status는 필수입니다.");

        validateContentBlock(blockType, contentBlockId);
        validateTableLocation(blockType, location, value);
    }

    public boolean verified() {
        return status == EvidenceStatus.VERIFIED;
    }

    private static void validateContentBlock(
            EvidenceBlockType blockType,
            UUID contentBlockId
    ) {
        if (blockType != EvidenceBlockType.DOCUMENT_METADATA
                && contentBlockId == null) {
            throw new IllegalArgumentException(
                    "원문 Evidence에는 contentBlockId가 필요합니다."
            );
        }
    }

    private static void validateTableLocation(
            EvidenceBlockType blockType,
            DisclosureEvidenceLocation location,
            DisclosureEvidenceValue value
    ) {
        boolean tableEvidence = switch (blockType) {
            case TABLE, TABLE_ROW, TABLE_CELL -> true;
            default -> false;
        };

        if (!tableEvidence && location.hasTableLocation()) {
            throw new IllegalArgumentException(
                    "TABLE 계열이 아닌 Evidence에는 표 위치를 지정할 수 없습니다."
            );
        }
        if (blockType == EvidenceBlockType.TABLE_ROW
                && location.tableRowIndex() == null) {
            throw new IllegalArgumentException(
                    "TABLE_ROW Evidence에는 tableRowIndex가 필요합니다."
            );
        }
        if (blockType == EvidenceBlockType.TABLE_CELL
                && (location.tableRowIndex() == null
                || location.tableCellIndex() == null)) {
            throw new IllegalArgumentException(
                    "TABLE_CELL Evidence에는 행과 셀 위치가 필요합니다."
            );
        }
        if ((blockType == EvidenceBlockType.TABLE_ROW
                || blockType == EvidenceBlockType.TABLE_CELL)
                && value.rowLabel() == null) {
            throw new IllegalArgumentException(
                    "표 행·셀 Evidence에는 rowLabel이 필요합니다."
            );
        }
    }

    private static UUID requireId(UUID value, String fieldName) {
        return Objects.requireNonNull(
                value,
                fieldName + "는 필수입니다."
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

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
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
