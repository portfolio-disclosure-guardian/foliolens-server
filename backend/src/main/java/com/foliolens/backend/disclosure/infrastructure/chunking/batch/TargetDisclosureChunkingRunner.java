package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.infrastructure.persistence
        .DisclosureChunkPersistenceResult;
import com.foliolens.backend.disclosure.service
        .DisclosureDocumentChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Order(8)
@ConditionalOnProperty(
        prefix = "foliolens.chunking.target",
        name = "enabled",
        havingValue = "true"
)
public class TargetDisclosureChunkingRunner
        implements ApplicationRunner {

    private final DisclosureDocumentChunkingService chunkingService;
    private final UUID documentId;

    public TargetDisclosureChunkingRunner(
            DisclosureDocumentChunkingService chunkingService,
            @Value("${foliolens.chunking.target.document-id}")
            UUID documentId
    ) {
        this.chunkingService = chunkingService;
        this.documentId = documentId;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "특정 문서 청킹을 시작합니다. documentId={}",
                documentId
        );

        DisclosureChunkPersistenceResult result =
                chunkingService.generateAndStore(documentId);

        log.info(
                "특정 문서 청킹이 완료됐습니다. "
                        + "documentId={}, deletedChunkCount={}, "
                        + "savedChunkCount={}, savedSourceCount={}",
                result.disclosureDocumentId(),
                result.deletedChunkCount(),
                result.savedChunkCount(),
                result.savedSourceCount()
        );
    }
}