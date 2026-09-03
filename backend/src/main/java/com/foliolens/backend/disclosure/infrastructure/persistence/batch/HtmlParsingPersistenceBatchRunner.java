package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(8)
@ConditionalOnProperty(prefix = "foliolens.parsing.html-persistence.batch", name = "enabled", havingValue = "true")
public class HtmlParsingPersistenceBatchRunner implements ApplicationRunner {
    private final HtmlParsingPersistenceBatchService service;
    private final int batchSize;
    private final int maxDocuments;
    private final int maxFailures;
    private final String subtype;

    public HtmlParsingPersistenceBatchRunner(HtmlParsingPersistenceBatchService service,
            @Value("${foliolens.parsing.html-persistence.batch.batch-size:10}") int batchSize,
            @Value("${foliolens.parsing.html-persistence.batch.max-documents:43}") int maxDocuments,
            @Value("${foliolens.parsing.html-persistence.batch.max-failures:5}") int maxFailures,
            @Value("${foliolens.parsing.html-persistence.batch.raw-subtype:신규시설투자등}") String subtype) {
        if (batchSize < 1 || batchSize > 100 || maxDocuments < 1 || maxFailures < 1
                || subtype == null || subtype.isBlank()) {
            throw new IllegalArgumentException("HTML 적재 배치 설정이 올바르지 않습니다.");
        }
        this.service = service;
        this.batchSize = batchSize;
        this.maxDocuments = maxDocuments;
        this.maxFailures = maxFailures;
        this.subtype = subtype;
    }

    @Override public void run(ApplicationArguments args) {
        int processed = 0;
        int failed = 0;
        long sections = 0;
        long blocks = 0;
        while (processed < maxDocuments && failed < maxFailures) {
            var result = service.persistNextBatch(
                    Math.min(batchSize, Math.min(maxDocuments - processed, maxFailures - failed)), subtype);
            if (result.totalCount() == 0) break;
            processed += result.totalCount();
            failed += result.failures().size();
            sections += result.savedSectionCount();
            blocks += result.savedBlockCount();
            result.failures().forEach((id, error) ->
                    log.warn("HTML 적재 실패: documentId={}, reason={}", id, error));
            log.info("HTML 적재 배치: 누적={}, 성공={}, 실패={}, 저장 섹션={}, 저장 블록={}",
                    processed, processed - failed, failed, sections, blocks);
        }
        log.info("HTML 적재 종료: totalCount={}, successCount={}, failedCount={}", processed, processed - failed, failed);
    }
}
