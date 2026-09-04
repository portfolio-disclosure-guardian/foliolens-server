package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentChunkingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class DisclosureChunkingBatchService {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureDocumentChunkingService chunkingService;

    public DisclosureChunkingBatchService(
            DisclosureDocumentRepository documentRepository,
            DisclosureDocumentChunkingService chunkingService
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.chunkingService = Objects.requireNonNull(
                chunkingService,
                "chunkingService는 필수입니다."
        );
    }

    /**
     * 기존 호출은 DART XML 전체 유형을 대상으로 한다.
     */
    public DisclosureChunkingBatchResult processNextBatch(int batchSize) {
        return processNextBatch(batchSize, DisclosureDocumentContentFormat.DART_XML, null);
    }

    /**
     * 파싱이 완료되고 청킹 상태가 PENDING인 선택 형식·유형의 문서를
     * ID 순서로 제한된 수만큼 처리한다.
     *
     * 처리된 문서는 COMPLETED 또는 FAILED가 되므로 다음 호출에서도
     * 페이지를 이동하지 않고 항상 첫 페이지를 조회한다.
     */
    public DisclosureChunkingBatchResult processNextBatch(
            int batchSize,
            DisclosureDocumentContentFormat contentFormat,
            String rawSubtype
    ) {
        validateBatchSize(batchSize);
        validateContentFormat(contentFormat);
        String subtype = normalizeSubtype(rawSubtype);

        Instant startedAt = Instant.now();

        List<DisclosureDocument> documents = documentRepository
                .findChunkingTargets(
                        contentFormat,
                        subtype,
                        PageRequest.of(0, batchSize)
                )
                .getContent();

        List<DisclosureChunkingBatchRow> rows =
                new ArrayList<>(documents.size());

        for (DisclosureDocument document : documents) {
            rows.add(processDocument(document));
        }

        return new DisclosureChunkingBatchResult(
                startedAt,
                Instant.now(),
                rows
        );
    }

    static DisclosureDocumentContentFormat validateContentFormat(DisclosureDocumentContentFormat format) {
        if (format != DisclosureDocumentContentFormat.DART_XML
                && format != DisclosureDocumentContentFormat.HTML
                && format != DisclosureDocumentContentFormat.PDF) {
            throw new IllegalArgumentException("청킹 contentFormat은 DART_XML, HTML 또는 PDF이어야 합니다.");
        }
        return format;
    }

    static String normalizeSubtype(String rawSubtype) {
        return rawSubtype == null || rawSubtype.isBlank() ? null : rawSubtype.strip();
    }

    private DisclosureChunkingBatchRow processDocument(
            DisclosureDocument document
    ) {
        Objects.requireNonNull(
                document,
                "조회된 문서 목록에는 null이 들어갈 수 없습니다."
        );

        long startedNanos = System.nanoTime();

        try {
            DisclosureChunkPersistenceResult result =
                    chunkingService.generateAndStore(document.getId());

            return DisclosureChunkingBatchRow.success(
                    document,
                    result,
                    elapsedMillis(startedNanos)
            );
        } catch (RuntimeException exception) {
            return DisclosureChunkingBatchRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    extractErrorMessage(exception)
            );
        }
    }

    private void validateBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize는 1~"
                            + MAX_BATCH_SIZE
                            + " 범위여야 합니다."
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos
        );
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

        if (result.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return result;
        }

        return result.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
