package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkPersistenceResult;

import java.util.Objects;
import java.util.UUID;

/**
 * 청킹 배치에서 원문 문서 한 건을 처리한 결과다.
 */
public record DisclosureChunkingBatchRow(
        UUID disclosureDocumentId,
        String receiptNo,
        String fileName,
        DisclosureChunkingBatchStatus status,
        int deletedChunkCount,
        int savedChunkCount,
        int savedSourceCount,
        long elapsedMillis,
        String errorMessage
) {

    public DisclosureChunkingBatchRow {
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        receiptNo = requireText(receiptNo, "receiptNo");
        fileName = requireText(fileName, "fileName");
        status = Objects.requireNonNull(status, "status는 필수입니다.");

        if (deletedChunkCount < 0
                || savedChunkCount < 0
                || savedSourceCount < 0) {
            throw new IllegalArgumentException(
                    "청킹 결과 개수는 0 이상이어야 합니다."
            );
        }

        if (elapsedMillis < 0) {
            throw new IllegalArgumentException(
                    "elapsedMillis는 0 이상이어야 합니다."
            );
        }

        if (status == DisclosureChunkingBatchStatus.SUCCESS) {
            errorMessage = null;
        } else {
            errorMessage = normalizeErrorMessage(errorMessage);

            if (deletedChunkCount != 0
                    || savedChunkCount != 0
                    || savedSourceCount != 0) {
                throw new IllegalArgumentException(
                        "실패한 청킹 결과의 저장 개수는 0이어야 합니다."
                );
            }
        }
    }

    public static DisclosureChunkingBatchRow success(
            DisclosureDocument document,
            DisclosureChunkPersistenceResult result,
            long elapsedMillis
    ) {
        Objects.requireNonNull(document, "document는 필수입니다.");
        Objects.requireNonNull(result, "result는 필수입니다.");

        UUID documentId = Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 문서는 배치 결과로 만들 수 없습니다."
        );

        if (!documentId.equals(result.disclosureDocumentId())) {
            throw new IllegalArgumentException(
                    "문서와 청크 저장 결과의 documentId가 다릅니다."
            );
        }

        return new DisclosureChunkingBatchRow(
                documentId,
                receiptNoOf(document),
                document.getFileName(),
                DisclosureChunkingBatchStatus.SUCCESS,
                result.deletedChunkCount(),
                result.savedChunkCount(),
                result.savedSourceCount(),
                elapsedMillis,
                null
        );
    }

    public static DisclosureChunkingBatchRow failed(
            DisclosureDocument document,
            long elapsedMillis,
            String errorMessage
    ) {
        Objects.requireNonNull(document, "document는 필수입니다.");

        return new DisclosureChunkingBatchRow(
                Objects.requireNonNull(
                        document.getId(),
                        "저장되지 않은 문서는 배치 결과로 만들 수 없습니다."
                ),
                receiptNoOf(document),
                document.getFileName(),
                DisclosureChunkingBatchStatus.FAILED,
                0,
                0,
                0,
                elapsedMillis,
                errorMessage
        );
    }

    private static String receiptNoOf(DisclosureDocument document) {
        Disclosure disclosure = Objects.requireNonNull(
                document.getDisclosure(),
                "DisclosureDocument의 disclosure는 필수입니다."
        );
        return disclosure.getReceiptNo();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "는 필수입니다."
            );
        }

        return value.trim();
    }

    private static String normalizeErrorMessage(String value) {
        if (value == null || value.isBlank()) {
            return "상세 오류 메시지가 없습니다.";
        }

        return value.trim();
    }
}
