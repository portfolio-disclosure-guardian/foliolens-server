package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.infrastructure.parsing.DartXmlDisclosureParser;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingFailureRecorder;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 파서와 저장 서비스를 연결하는 상위 서비스
 */
@Service
public class DisclosureDocumentParsingService {

    private static final String PARSER_NAME = "DartXmlDisclosureParser";

    private static final String PARSER_VERSION = "1.0.0";

    private final DartXmlDisclosureParser parser;
    private final DisclosureParsingPersistenceService persistenceService;
    private final DisclosureParsingFailureRecorder failureRecorder;

    public DisclosureDocumentParsingService(
            DartXmlDisclosureParser parser,
            DisclosureParsingPersistenceService persistenceService,
            DisclosureParsingFailureRecorder failureRecorder
    ) {
        this.parser = parser;
        this.persistenceService = persistenceService;
        this.failureRecorder = failureRecorder;
    }

    public DisclosureParsingPersistenceResult parseAndStore(
            UUID disclosureDocumentId,
            Path sourceFile
    ) {
        try {
            /*
             * XML 파싱은 DB 트랜잭션 밖에서 수행한다.
             */
            ParsedDisclosureDocument parsedDocument = parser.parse(sourceFile);

            /*
             * 실제 DB 삭제·저장 구간만 트랜잭션으로 수행한다.
             */
            return persistenceService.replaceParsedResult(
                    disclosureDocumentId,
                    parsedDocument,
                    PARSER_NAME,
                    PARSER_VERSION
            );

        } catch (RuntimeException exception) {
            markFailed(disclosureDocumentId, exception);

            throw exception;
        }
    }

    /**
     * 파일 경로 확인처럼 파서 호출 전에 발생한 실패도
     * 동일한 파서 메타데이터로 기록한다.
     */
    public void markFailed(
            UUID disclosureDocumentId,
            Throwable throwable
    ) {
        failureRecorder.markFailed(
                disclosureDocumentId,
                PARSER_NAME,
                PARSER_VERSION,
                throwable
        );
    }
}
