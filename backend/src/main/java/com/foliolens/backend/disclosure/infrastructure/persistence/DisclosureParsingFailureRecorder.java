package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class DisclosureParsingFailureRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4_000;

    private final DisclosureDocumentRepository documentRepository;

    public DisclosureParsingFailureRecorder(
            DisclosureDocumentRepository documentRepository
    ) {
        this.documentRepository = documentRepository;
    }

    /**
     * 파싱 또는 저장 실패를 독립된 트랜잭션으로 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            UUID disclosureDocumentId,
            String parserName,
            String parserVersion,
            Throwable throwable
    ) {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );

        Objects.requireNonNull(
                throwable,
                "throwable은 필수입니다."
        );

        DisclosureDocument document = documentRepository.findById(disclosureDocumentId)
                .orElseThrow(() -> new IllegalStateException(
                        "실패 상태를 기록할 원문 문서를 찾을 수 없습니다."
                                + " disclosureDocumentId="
                                + disclosureDocumentId)
                );

        document.markFailed(
                parserName,
                parserVersion,
                extractErrorMessage(throwable),
                Instant.now()
        );

        documentRepository.flush();
    }

    private String extractErrorMessage(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null
                && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        String detail = rootCause.getMessage();

        if (detail == null || detail.isBlank()) {
            detail = "상세 오류 메시지가 없습니다.";
        }

        String result = rootCause.getClass().getSimpleName() + ": "
                + detail.trim();

        if (result.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return result.substring(
                    0,
                    MAX_ERROR_MESSAGE_LENGTH
            );
        }

        return result;
    }
}
