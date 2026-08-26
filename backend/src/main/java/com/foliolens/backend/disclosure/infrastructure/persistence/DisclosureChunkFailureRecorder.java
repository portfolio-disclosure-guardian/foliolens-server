package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureChunkingPolicy;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class DisclosureChunkFailureRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureChunkingPolicy policy;

    public DisclosureChunkFailureRecorder(
            DisclosureDocumentRepository documentRepository,
            DisclosureChunkingPolicy policy
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.policy = Objects.requireNonNull(
                policy,
                "policy는 필수입니다."
        );
    }

    /**
     * 청크 생성 또는 저장 실패를 독립된 트랜잭션으로 기록한다.
     *
     * 청크 교체 트랜잭션이 롤백되더라도 실패 상태는 남아야 하므로
     * REQUIRES_NEW를 사용한다. 기존에 정상 저장된 청크 행은 삭제하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            UUID disclosureDocumentId,
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

        DisclosureDocument document = documentRepository
                .findById(disclosureDocumentId)
                .orElseThrow(() -> new IllegalStateException(
                        "청킹 실패 상태를 기록할 원문 문서를 찾을 수 없습니다."
                                + " disclosureDocumentId="
                                + disclosureDocumentId
                ));

        document.markChunkingFailed(
                policy.generatorName(),
                policy.generatorVersion(),
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

        String result = rootCause.getClass().getSimpleName()
                + ": "
                + detail.trim();

        if (result.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return result.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }

        return result;
    }
}
