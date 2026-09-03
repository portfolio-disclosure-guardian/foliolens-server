package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.validation.HtmlParsingValidationBatchService;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HtmlParsingPersistenceBatchService {
    private final DisclosureDocumentRepository repository;
    private final DisclosureSourceFileResolver files;
    private final DisclosureDocumentParsingService parsing;

    public HtmlParsingPersistenceBatchService(DisclosureDocumentRepository repository,
                                             DisclosureSourceFileResolver files,
                                             DisclosureDocumentParsingService parsing) {
        this.repository = repository;
        this.files = files;
        this.parsing = parsing;
    }

    /** 성공/실패 후 PENDING에서 빠지므로 항상 첫 페이지를 조회한다. */
    public HtmlParsingPersistenceBatchResult persistNextBatch(int limit, String rawSubtype) {
        if (limit < 1 || limit > 100 || rawSubtype == null || rawSubtype.isBlank()) {
            throw new IllegalArgumentException("limit은 1~100, rawSubtype은 필수입니다.");
        }
        List<DisclosureParsingPersistenceResult> successes = new ArrayList<>();
        Map<UUID, String> failures = new LinkedHashMap<>();
        for (DisclosureDocument document : repository.findHtmlParsingTargets(
                rawSubtype.strip(), DisclosureDocumentParseStatus.PENDING, PageRequest.of(0, limit)).getContent()) {
            java.nio.file.Path source;
            try {
                source = files.resolve(document);
            } catch (RuntimeException exception) {
                parsing.markFailed(document.getId(), exception);
                failures.put(document.getId(), HtmlParsingValidationBatchService.describe(exception));
                continue;
            }
            try {
                successes.add(parsing.parseAndStore(document.getId(), source));
            } catch (RuntimeException exception) {
                failures.put(document.getId(), HtmlParsingValidationBatchService.describe(exception));
            }
        }
        return new HtmlParsingPersistenceBatchResult(successes, failures);
    }
}
