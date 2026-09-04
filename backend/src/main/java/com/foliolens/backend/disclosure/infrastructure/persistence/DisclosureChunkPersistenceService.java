package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureChunk;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureChunkingPolicy;
import com.foliolens.backend.disclosure.infrastructure.chunking.GeneratedDisclosureChunk;
import com.foliolens.backend.disclosure.repository.DisclosureChunkRepository;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DisclosureChunkPersistenceService {

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureSectionRepository sectionRepository;
    private final DisclosureContentBlockRepository blockRepository;
    private final DisclosureChunkRepository chunkRepository;
    private final GeneratedDisclosureChunkEntityMapper entityMapper;
    private final DisclosureChunkingPolicy policy;

    public DisclosureChunkPersistenceService(
            DisclosureDocumentRepository documentRepository,
            DisclosureSectionRepository sectionRepository,
            DisclosureContentBlockRepository blockRepository,
            DisclosureChunkRepository chunkRepository,
            GeneratedDisclosureChunkEntityMapper entityMapper,
            DisclosureChunkingPolicy policy
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
        this.chunkRepository = Objects.requireNonNull(
                chunkRepository,
                "chunkRepository는 필수입니다."
        );
        this.entityMapper = Objects.requireNonNull(
                entityMapper,
                "entityMapper는 필수입니다."
        );
        this.policy = Objects.requireNonNull(
                policy,
                "policy는 필수입니다."
        );
    }

    /**
     * 문서 하나의 기존 검색 청크를 새 생성 결과로 전체 교체한다.
     *
     * 기존 삭제, 새 청크와 출처 저장, COMPLETED 상태 변경은
     * 하나의 트랜잭션으로 처리된다. 중간에 실패하면 기존 청크 삭제까지
     * 모두 롤백된다.
     */
    @Transactional
    public DisclosureChunkPersistenceResult replaceChunks(
            UUID disclosureDocumentId,
            List<GeneratedDisclosureChunk> generatedChunks
    ) {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        Objects.requireNonNull(
                generatedChunks,
                "generatedChunks는 필수입니다."
        );

        List<GeneratedDisclosureChunk> chunks =
                List.copyOf(generatedChunks);

        DisclosureDocument currentDocument =
                findDocument(disclosureDocumentId);

        validateChunkable(currentDocument);
        validateGeneratorMetadata(chunks);

        int deletedChunkCount =
                chunkRepository.deleteAllByDisclosureDocumentId(
                        disclosureDocumentId
                );

        /*
         * 벌크 삭제의 clearAutomatically=true로 영속성 컨텍스트가
         * 초기화되므로 Document와 연관 데이터를 다시 조회한다.
         */
        DisclosureDocument document =
                findDocument(disclosureDocumentId);

        validateChunkable(document);

        List<DisclosureSection> sections =
                sectionRepository
                        .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
                                disclosureDocumentId
                        );

        List<DisclosureContentBlock> blocks =
                blockRepository
                        .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
                                disclosureDocumentId
                        );

        List<DisclosureChunk> entities =
                entityMapper.toEntities(
                        document,
                        sections,
                        blocks,
                        chunks
                );

        int savedSourceCount = entities.stream()
                .mapToInt(entity -> entity.getSources().size())
                .sum();

        chunkRepository.saveAll(entities);

        /*
         * 청크·출처 FK와 DB CHECK, GENERATED 컬럼 오류를
         * 완료 상태 변경 전에 확인한다.
         */
        chunkRepository.flush();

        document.markChunkingCompleted(
                policy.generatorName(),
                policy.generatorVersion(),
                Instant.now()
        );

        documentRepository.flush();

        return new DisclosureChunkPersistenceResult(
                disclosureDocumentId,
                deletedChunkCount,
                entities.size(),
                savedSourceCount
        );
    }

    private DisclosureDocument findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "청크를 저장할 원문 문서를 찾을 수 없습니다."
                                + " disclosureDocumentId="
                                + documentId
                ));
    }

    private void validateChunkable(DisclosureDocument document) {
        if (!document.isChunkable()) {
            throw new IllegalStateException(
                    "파싱이 완료된 문서만 청크를 저장할 수 있습니다."
                            + " disclosureDocumentId="
                            + document.getId()
                            + ", parseStatus="
                            + document.getParseStatus()
            );
        }
    }

    private void validateGeneratorMetadata(
            List<GeneratedDisclosureChunk> chunks
    ) {
        for (GeneratedDisclosureChunk chunk : chunks) {
            Objects.requireNonNull(
                    chunk,
                    "generatedChunks에는 null이 들어갈 수 없습니다."
            );

            if (!policy.generatorName().equals(chunk.generatorName())
                    || !policy.generatorVersion().equals(
                    chunk.generatorVersion()
            )) {
                throw new IllegalArgumentException(
                        "현재 정책과 다른 생성기 정보의 청크입니다."
                                + " chunkSequenceNo="
                                + chunk.chunkSequenceNo()
                                + ", generatorName="
                                + chunk.generatorName()
                                + ", generatorVersion="
                                + chunk.generatorVersion()
                );
            }
        }
    }
}
