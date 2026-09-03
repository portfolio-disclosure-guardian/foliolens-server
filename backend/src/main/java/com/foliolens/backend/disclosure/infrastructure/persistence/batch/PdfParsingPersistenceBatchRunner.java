package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** PDF만 한 문서씩 처리. 뒤의 @Order(8) 공통 청킹 Runner와 같은 실행에서 연결할 수 있다. */
@Slf4j
@Component
@Order(7)
@ConditionalOnProperty(prefix = "foliolens.parsing.pdf-persistence", name = "enabled", havingValue = "true")
public class PdfParsingPersistenceBatchRunner implements ApplicationRunner {
    private final DisclosureDocumentRepository repository;
    private final DisclosureSourceFileResolver files;
    private final DisclosureDocumentParsingService parsing;
    private final int maxDocuments;

    public PdfParsingPersistenceBatchRunner(DisclosureDocumentRepository repository,
            DisclosureSourceFileResolver files, DisclosureDocumentParsingService parsing,
            @Value("${foliolens.parsing.pdf-persistence.max-documents:3}") int maxDocuments) {
        if (maxDocuments < 1 || maxDocuments > 100) {
            throw new IllegalArgumentException("PDF max-documents는 1~100이어야 합니다.");
        }
        this.repository = repository;
        this.files = files;
        this.parsing = parsing;
        this.maxDocuments = maxDocuments;
    }

    @Override public void run(ApplicationArguments args) {
        int processed = 0;
        int failed = 0;
        long started = System.nanoTime();
        for (; processed < maxDocuments;) {
            var targets = repository.findAllByContentFormatAndParseStatusOrderByIdAsc(
                    DisclosureDocumentContentFormat.PDF, DisclosureDocumentParseStatus.PENDING,
                    PageRequest.of(0, 1));
            if (targets.isEmpty()) break;
            DisclosureDocument document = targets.getContent().getFirst();
            processed++;
            try {
                Path source;
                try { source = files.resolve(document); }
                catch (RuntimeException error) {
                    parsing.markFailed(document.getId(), error);
                    throw error;
                }
                var result = parsing.parseAndStore(document.getId(), source);
                log.info("PDF 텍스트 부분 추출 저장: documentId={}, savedSections={}, savedBlocks={}, parseStatus=PARTIAL",
                        document.getId(), result.savedSectionCount(), result.savedBlockCount());
            } catch (RuntimeException error) {
                failed++;
                log.warn("PDF 적재 실패로 중단: documentId={}", document.getId(), error);
                break; // 3건의 최소 적재이므로 첫 실패에서 중단해 확인한다.
            }
        }
        log.info("PDF 텍스트 적재 종료: totalCount={}, savedPartialCount={}, failedCount={}, elapsedMillis={}",
                processed, processed - failed, failed, (System.nanoTime() - started) / 1_000_000);
    }
}
