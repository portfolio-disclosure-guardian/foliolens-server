package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.DisclosureDocumentParser;
import com.foliolens.backend.disclosure.infrastructure.parsing.DisclosureDocumentParserRouter;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.DartHtmlDisclosureParser;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.validation.HtmlParsingValidator;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingFailureRecorder;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceService;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/** 형식별 파싱은 트랜잭션 밖에서, 삭제·저장·상태 갱신은 문서별 트랜잭션에서 수행한다. */
@Service
public class DisclosureDocumentParsingService {
    private final DisclosureDocumentParserRouter router;
    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureParsingPersistenceService persistenceService;
    private final DisclosureParsingFailureRecorder failureRecorder;
    private final HtmlParsingValidator htmlValidator;

    public DisclosureDocumentParsingService(DisclosureDocumentParserRouter router,
                                            DisclosureDocumentRepository documentRepository,
                                            DisclosureParsingPersistenceService persistenceService,
                                            DisclosureParsingFailureRecorder failureRecorder,
                                            HtmlParsingValidator htmlValidator) {
        this.router = router;
        this.documentRepository = documentRepository;
        this.persistenceService = persistenceService;
        this.failureRecorder = failureRecorder;
        this.htmlValidator = htmlValidator;
    }

    public DisclosureParsingPersistenceResult parseAndStore(UUID disclosureDocumentId, Path sourceFile) {
        DisclosureDocumentParser parser = null;
        try {
            DisclosureDocument document = findDocument(disclosureDocumentId);
            parser = router.select(document);
            ParsedDisclosureDocument parsed = parser instanceof DartHtmlDisclosureParser
                    ? htmlValidator.validate(sourceFile).document() : parser.parse(sourceFile);
            if (parser instanceof DartHtmlDisclosureParser && document.getDisclosure().isCorrection()
                    && parsed.sections().stream().noneMatch(s -> "정정신고(보고)".equals(s.title()))) {
                throw new IllegalArgumentException("정정공시의 정정 섹션이 누락되었습니다.");
            }
            return persistenceService.replaceParsedResult(disclosureDocumentId, parsed,
                    parser.parserName(), parser.parserVersion());
        } catch (RuntimeException exception) {
            recordFailure(disclosureDocumentId, parser, exception);
            throw exception;
        }
    }

    public void markFailed(UUID documentId, Throwable throwable) {
        DisclosureDocumentParser parser = null;
        try {
            parser = router.select(findDocument(documentId));
        } catch (RuntimeException routingFailure) {
            throwable.addSuppressed(routingFailure);
        }
        recordFailure(documentId, parser, throwable);
    }

    private DisclosureDocument findDocument(UUID id) {
        return documentRepository.findWithDisclosureById(id).orElseThrow(() ->
                new IllegalArgumentException("원문 문서를 찾을 수 없습니다: " + id));
    }

    private void recordFailure(UUID id, DisclosureDocumentParser parser, Throwable error) {
        try {
            failureRecorder.markFailed(id,
                    parser == null ? "DisclosureDocumentParserRouter" : parser.parserName(),
                    parser == null ? "1.0.0" : parser.parserVersion(), error);
        } catch (RuntimeException recordingFailure) {
            // 상태 기록 장애가 원래 파싱/저장 실패 원인을 덮지 않도록 한다.
            error.addSuppressed(recordingFailure);
        }
    }
}
