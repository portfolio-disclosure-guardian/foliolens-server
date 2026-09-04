package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureChunkGenerator;
import com.foliolens.backend.disclosure.infrastructure.chunking.GeneratedDisclosureChunk;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkFailureRecorder;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkPersistenceResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkPersistenceService;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 문서 하나의 파싱 결과 조회, 검색 청크 생성과 저장을 연결하는 상위 서비스다.
 */
@Service
public class DisclosureDocumentChunkingService {

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureSectionRepository sectionRepository;
    private final DisclosureContentBlockRepository blockRepository;
    private final DisclosureChunkGenerator chunkGenerator;
    private final DisclosureChunkPersistenceService persistenceService;
    private final DisclosureChunkFailureRecorder failureRecorder;

    public DisclosureDocumentChunkingService(
            DisclosureDocumentRepository documentRepository,
            DisclosureSectionRepository sectionRepository,
            DisclosureContentBlockRepository blockRepository,
            DisclosureChunkGenerator chunkGenerator,
            DisclosureChunkPersistenceService persistenceService,
            DisclosureChunkFailureRecorder failureRecorder
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.sectionRepository = Objects.requireNonNull(
                sectionRepository,
                "sectionRepository는 필수입니다."
        );
        this.blockRepository = Objects.requireNonNull(
                blockRepository,
                "blockRepository는 필수입니다."
        );
        this.chunkGenerator = Objects.requireNonNull(
                chunkGenerator,
                "chunkGenerator는 필수입니다."
        );
        this.persistenceService = Objects.requireNonNull(
                persistenceService,
                "persistenceService는 필수입니다."
        );
        this.failureRecorder = Objects.requireNonNull(
                failureRecorder,
                "failureRecorder는 필수입니다."
        );
    }

    /**
     * 파싱이 완료된 문서 하나의 검색 청크를 생성하고 DB 결과를 교체한다.
     *
     * 생성은 긴 DB 트랜잭션을 만들지 않도록 트랜잭션 밖에서 수행하고,
     * 실제 삭제·저장은 DisclosureChunkPersistenceService에 위임한다.
     */
    public DisclosureChunkPersistenceResult generateAndStore(
            UUID disclosureDocumentId
    ) {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );

        DisclosureDocument document = findDocument(disclosureDocumentId);
        validateChunkable(document);

        try {
            List<DisclosureSection> sections = sectionRepository
                    .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
                            disclosureDocumentId
                    );

            List<DisclosureContentBlock> blocks = blockRepository
                    .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
                            disclosureDocumentId
                    );

            List<GeneratedDisclosureChunk> generatedChunks =
                    chunkGenerator.generateChunks(
                            disclosureDocumentId,
                            sections,
                            blocks
                    );

            return persistenceService.replaceChunks(
                    disclosureDocumentId,
                    generatedChunks
            );
        } catch (RuntimeException exception) {
            failureRecorder.markFailed(
                    disclosureDocumentId,
                    exception
            );
            throw exception;
        }
    }

    private DisclosureDocument findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "청크를 생성할 원문 문서를 찾을 수 없습니다."
                                + " disclosureDocumentId="
                                + documentId
                ));
    }

    private void validateChunkable(DisclosureDocument document) {
        if (!document.isChunkable()) {
            throw new IllegalStateException(
                    "파싱이 완료된 문서만 청크를 생성할 수 있습니다."
                            + " disclosureDocumentId="
                            + document.getId()
                            + ", parseStatus="
                            + document.getParseStatus()
            );
        }
    }
}
