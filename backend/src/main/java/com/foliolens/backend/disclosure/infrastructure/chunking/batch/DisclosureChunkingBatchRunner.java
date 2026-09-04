package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@Order(8)
@ConditionalOnProperty(
        prefix = "foliolens.chunking.batch",
        name = "enabled",
        havingValue = "true"
)
public class DisclosureChunkingBatchRunner implements ApplicationRunner {

    private static final int MAX_FAILURE_LOG_COUNT = 10;
    private static final int MAX_BATCH_SIZE = 100;

    private final DisclosureChunkingBatchService batchService;
    private final int batchSize;
    private final int maxDocuments;
    private final int maxFailures;
    private final DisclosureDocumentContentFormat contentFormat;
    private final String rawSubtype;

    public DisclosureChunkingBatchRunner(
            DisclosureChunkingBatchService batchService,
            @Value("${foliolens.chunking.batch.batch-size:10}")
            int batchSize,
            @Value("${foliolens.chunking.batch.max-documents:50}")
            int maxDocuments,
            @Value("${foliolens.chunking.batch.max-failures:5}")
            int maxFailures,
            @Value("${foliolens.chunking.batch.content-format:DART_XML}")
            DisclosureDocumentContentFormat contentFormat,
            @Value("${foliolens.chunking.batch.raw-subtype:}")
            String rawSubtype
    ) {
        this.batchService = Objects.requireNonNull(
                batchService,
                "batchService는 필수입니다."
        );
        this.batchSize = validateBatchSize(batchSize);
        this.maxDocuments = validateNonNegative(
                maxDocuments,
                "maxDocuments"
        );
        this.maxFailures = validateNonNegative(
                maxFailures,
                "maxFailures"
        );
        this.contentFormat = DisclosureChunkingBatchService.validateContentFormat(contentFormat);
        this.rawSubtype = DisclosureChunkingBatchService.normalizeSubtype(rawSubtype);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "검색 청크 생성을 시작합니다. "
                        + "batchSize={}, maxDocuments={}, maxFailures={}, contentFormat={}, rawSubtype={}",
                batchSize,
                maxDocuments,
                maxFailures,
                contentFormat,
                rawSubtype == null ? "ALL" : rawSubtype
        );

        Instant startedAt = Instant.now();
        List<DisclosureChunkingBatchRow> accumulatedRows =
                new ArrayList<>();
        int batchNumber = 0;
        boolean stoppedByFailureThreshold = false;

        while (canProcessMore(accumulatedRows.size())) {
            int requestedBatchSize = calculateRequestedBatchSize(
                    accumulatedRows.size()
            );

            DisclosureChunkingBatchResult batchResult =
                    batchService.processNextBatch(requestedBatchSize, contentFormat, rawSubtype);

            if (batchResult.totalCount() == 0) {
                break;
            }

            batchNumber++;
            accumulatedRows.addAll(batchResult.rows());

            log.info(
                    "검색 청크 배치가 완료됐습니다. "
                            + "batchNumber={}, batchCount={}, "
                            + "cumulativeCount={}, successCount={}, "
                            + "failedCount={}, deletedChunkCount={}, "
                            + "savedChunkCount={}, savedSourceCount={}, "
                            + "elapsedMillis={}",
                    batchNumber,
                    batchResult.totalCount(),
                    accumulatedRows.size(),
                    batchResult.successCount(),
                    batchResult.failedCount(),
                    batchResult.deletedChunkCount(),
                    batchResult.savedChunkCount(),
                    batchResult.savedSourceCount(),
                    batchResult.elapsedMillis()
            );

            long cumulativeFailureCount = accumulatedRows.stream()
                    .filter(row ->
                            row.status()
                                    == DisclosureChunkingBatchStatus.FAILED
                    )
                    .count();

            if (maxFailures > 0
                    && cumulativeFailureCount >= maxFailures) {
                stoppedByFailureThreshold = true;
                break;
            }

            if (batchResult.totalCount() < requestedBatchSize) {
                break;
            }
        }

        DisclosureChunkingBatchResult result =
                new DisclosureChunkingBatchResult(
                        startedAt,
                        Instant.now(),
                        accumulatedRows
                );

        if (result.totalCount() == 0) {
            log.info("청킹할 PENDING 문서가 없습니다. contentFormat={}, rawSubtype={}",
                    contentFormat, rawSubtype == null ? "ALL" : rawSubtype);
            return;
        }

        logFailures(result);

        if (stoppedByFailureThreshold) {
            log.warn(
                    "실패 임계치에 도달해 검색 청크 생성을 중단했습니다. "
                            + "failedCount={}, maxFailures={}",
                    result.failedCount(),
                    maxFailures
            );
        }

        if (result.hasFailures()) {
            log.warn(
                    "검색 청크 생성이 일부 실패로 완료됐습니다. "
                            + "totalCount={}, successCount={}, failedCount={}, "
                            + "deletedChunkCount={}, savedChunkCount={}, "
                            + "savedSourceCount={}, elapsedMillis={}",
                    result.totalCount(),
                    result.successCount(),
                    result.failedCount(),
                    result.deletedChunkCount(),
                    result.savedChunkCount(),
                    result.savedSourceCount(),
                    result.elapsedMillis()
            );
            return;
        }

        log.info(
                "검색 청크 생성이 완료됐습니다. "
                        + "totalCount={}, successCount={}, "
                        + "deletedChunkCount={}, savedChunkCount={}, "
                        + "savedSourceCount={}, elapsedMillis={}",
                result.totalCount(),
                result.successCount(),
                result.deletedChunkCount(),
                result.savedChunkCount(),
                result.savedSourceCount(),
                result.elapsedMillis()
        );
    }

    private boolean canProcessMore(int processedCount) {
        return maxDocuments == 0 || processedCount < maxDocuments;
    }

    private int calculateRequestedBatchSize(int processedCount) {
        if (maxDocuments == 0) {
            return batchSize;
        }

        return Math.min(
                batchSize,
                maxDocuments - processedCount
        );
    }

    private static int validateBatchSize(int value) {
        if (value < 1 || value > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize는 1~"
                            + MAX_BATCH_SIZE
                            + " 범위여야 합니다."
            );
        }

        return value;
    }

    private static int validateNonNegative(
            int value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0 이상이어야 합니다."
            );
        }

        return value;
    }

    private void logFailures(DisclosureChunkingBatchResult result) {
        result.rows().stream()
                .filter(row ->
                        row.status()
                                == DisclosureChunkingBatchStatus.FAILED
                )
                .limit(MAX_FAILURE_LOG_COUNT)
                .forEach(row -> log.warn(
                        "검색 청크 생성 실패: documentId={}, receiptNo={}, "
                                + "fileName={}, elapsedMillis={}, reason={}",
                        row.disclosureDocumentId(),
                        row.receiptNo(),
                        row.fileName(),
                        row.elapsedMillis(),
                        row.errorMessage()
                ));
    }
}
