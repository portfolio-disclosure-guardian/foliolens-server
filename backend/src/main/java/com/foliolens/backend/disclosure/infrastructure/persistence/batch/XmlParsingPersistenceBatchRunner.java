package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

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

@Slf4j
@Component
@Order(7)
@ConditionalOnProperty(
        prefix = "foliolens.parsing.xml-persistence.batch",
        name = "enabled",
        havingValue = "true"
)
public class XmlParsingPersistenceBatchRunner
        implements ApplicationRunner {

    private static final int MAX_FAILURE_LOG_COUNT = 10;

    private final XmlParsingPersistenceBatchService batchService;
    private final int chunkSize;
    private final int maxDocuments;
    private final int maxFailures;

    public XmlParsingPersistenceBatchRunner(
            XmlParsingPersistenceBatchService batchService,
            @Value(
                    "${foliolens.parsing.xml-persistence.batch.chunk-size:50}"
            )
            int chunkSize,
            @Value(
                    "${foliolens.parsing.xml-persistence.batch.max-documents:500}"
            )
            int maxDocuments,
            @Value(
                    "${foliolens.parsing.xml-persistence.batch.max-failures:10}"
            )
            int maxFailures
    ) {
        this.batchService = batchService;
        this.chunkSize = chunkSize;
        this.maxDocuments = validateNonNegative(
                maxDocuments,
                "maxDocuments"
        );
        this.maxFailures = validateNonNegative(
                maxFailures,
                "maxFailures"
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "XML 파싱 결과 DB 적재를 시작합니다. "
                        + "chunkSize={}, maxDocuments={}, maxFailures={}",
                chunkSize,
                maxDocuments,
                maxFailures
        );

        Instant startedAt = Instant.now();
        List<XmlParsingPersistenceRow> accumulatedRows =
                new ArrayList<>();
        int chunkNumber = 0;
        boolean stoppedByFailureThreshold = false;

        while (canProcessMore(accumulatedRows.size())) {
            int requestedChunkSize = calculateRequestedChunkSize(
                    accumulatedRows.size()
            );

            XmlParsingPersistenceBatchResult chunkResult =
                    batchService.persistNextChunk(
                            requestedChunkSize
                    );

            if (chunkResult.totalCount() == 0) {
                break;
            }

            chunkNumber++;
            accumulatedRows.addAll(chunkResult.rows());

            log.info(
                    "XML 파싱 결과 DB 적재 청크가 완료됐습니다. "
                            + "chunkNumber={}, chunkCount={}, "
                            + "cumulativeCount={}, successCount={}, "
                            + "failedCount={}, savedSectionCount={}, "
                            + "savedBlockCount={}, elapsedMillis={}",
                    chunkNumber,
                    chunkResult.totalCount(),
                    accumulatedRows.size(),
                    chunkResult.successCount(),
                    chunkResult.failedCount(),
                    chunkResult.savedSectionCount(),
                    chunkResult.savedBlockCount(),
                    chunkResult.elapsedMillis()
            );

            long cumulativeFailureCount = accumulatedRows.stream()
                    .filter(row ->
                            row.status()
                                    == XmlParsingPersistenceStatus.FAILED
                    )
                    .count();

            if (
                    maxFailures > 0
                            && cumulativeFailureCount >= maxFailures
            ) {
                stoppedByFailureThreshold = true;
                break;
            }

            if (chunkResult.totalCount() < requestedChunkSize) {
                break;
            }
        }

        XmlParsingPersistenceBatchResult result =
                new XmlParsingPersistenceBatchResult(
                        startedAt,
                        Instant.now(),
                        accumulatedRows
                );

        if (result.totalCount() == 0) {
            log.info(
                    "저장할 PENDING DART XML 문서가 없습니다."
            );
            return;
        }

        logFailures(result);

        if (stoppedByFailureThreshold) {
            log.warn(
                    "실패 임계치에 도달해 XML 파싱 결과 DB 적재를 "
                            + "중단했습니다. failedCount={}, maxFailures={}",
                    result.failedCount(),
                    maxFailures
            );
        }

        if (result.hasFailures()) {
            log.warn(
                    "XML 파싱 결과 DB 적재가 일부 실패로 완료됐습니다. "
                            + "totalCount={}, successCount={}, failedCount={}, "
                            + "savedSectionCount={}, savedBlockCount={}, "
                            + "elapsedMillis={}",
                    result.totalCount(),
                    result.successCount(),
                    result.failedCount(),
                    result.savedSectionCount(),
                    result.savedBlockCount(),
                    result.elapsedMillis()
            );

            return;
        }

        log.info(
                "XML 파싱 결과 DB 적재가 완료됐습니다. "
                        + "totalCount={}, successCount={}, "
                        + "savedSectionCount={}, savedBlockCount={}, "
                        + "elapsedMillis={}",
                result.totalCount(),
                result.successCount(),
                result.savedSectionCount(),
                result.savedBlockCount(),
                result.elapsedMillis()
        );
    }

    private boolean canProcessMore(int processedCount) {
        return maxDocuments == 0 || processedCount < maxDocuments;
    }

    private int calculateRequestedChunkSize(int processedCount) {
        if (maxDocuments == 0) {
            return chunkSize;
        }

        return Math.min(
                chunkSize,
                maxDocuments - processedCount
        );
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

    private void logFailures(
            XmlParsingPersistenceBatchResult result
    ) {
        result.rows().stream()
                .filter(row ->
                        row.status()
                                == XmlParsingPersistenceStatus.FAILED
                )
                .limit(MAX_FAILURE_LOG_COUNT)
                .forEach(row -> log.warn(
                        "XML 파싱 결과 DB 적재 실패: "
                                + "documentId={}, receiptNo={}, "
                                + "fileName={}, elapsedMillis={}, reason={}",
                        row.disclosureDocumentId(),
                        row.receiptNo(),
                        row.fileName(),
                        row.elapsedMillis(),
                        row.errorMessage()
                ));
    }
}
