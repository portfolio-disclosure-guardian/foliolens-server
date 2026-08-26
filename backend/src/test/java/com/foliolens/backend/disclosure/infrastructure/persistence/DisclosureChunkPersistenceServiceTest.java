package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureChunk;
import com.foliolens.backend.disclosure.domain.DisclosureChunkSource;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkPersistenceServiceTest {

    private static final UUID DOCUMENT_ID = new UUID(500, 1);

    private DisclosureDocumentRepository documentRepository;
    private DisclosureSectionRepository sectionRepository;
    private DisclosureContentBlockRepository blockRepository;
    private DisclosureChunkRepository chunkRepository;
    private GeneratedDisclosureChunkEntityMapper entityMapper;
    private DisclosureChunkingPolicy policy;
    private DisclosureChunkPersistenceService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        sectionRepository = mock(DisclosureSectionRepository.class);
        blockRepository = mock(DisclosureContentBlockRepository.class);
        chunkRepository = mock(DisclosureChunkRepository.class);
        entityMapper = mock(GeneratedDisclosureChunkEntityMapper.class);
        policy = DisclosureChunkingPolicy.dartXmlV1();

        service = new DisclosureChunkPersistenceService(
                documentRepository,
                sectionRepository,
                blockRepository,
                chunkRepository,
                entityMapper,
                policy
        );
    }

    @Test
    void replacesChunksAndMarksDocumentCompleted() {
        DisclosureDocument document = completedDocument();
        DisclosureSection section = mock(DisclosureSection.class);
        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        GeneratedDisclosureChunk generatedChunk = generatedChunk(
                policy.generatorName(),
                policy.generatorVersion()
        );
        DisclosureChunk firstEntity = chunkEntityWithSourceCount(2);
        DisclosureChunk secondEntity = chunkEntityWithSourceCount(1);

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        when(chunkRepository.deleteAllByDisclosureDocumentId(DOCUMENT_ID))
                .thenReturn(4);
        when(sectionRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(section));
        when(blockRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(block));
        when(entityMapper.toEntities(
                document,
                List.of(section),
                List.of(block),
                List.of(generatedChunk)
        )).thenReturn(List.of(firstEntity, secondEntity));

        DisclosureChunkPersistenceResult result =
                service.replaceChunks(
                        DOCUMENT_ID,
                        List.of(generatedChunk)
                );

        assertThat(result.disclosureDocumentId())
                .isEqualTo(DOCUMENT_ID);
        assertThat(result.deletedChunkCount()).isEqualTo(4);
        assertThat(result.savedChunkCount()).isEqualTo(2);
        assertThat(result.savedSourceCount()).isEqualTo(3);

        verify(chunkRepository).saveAll(
                List.of(firstEntity, secondEntity)
        );
        verify(chunkRepository).flush();

        ArgumentCaptor<Instant> chunkedAtCaptor =
                ArgumentCaptor.forClass(Instant.class);

        verify(document).markChunkingCompleted(
                eq(policy.generatorName()),
                eq(policy.generatorVersion()),
                chunkedAtCaptor.capture()
        );
        assertThat(chunkedAtCaptor.getValue()).isNotNull();
        verify(documentRepository).flush();
    }

    @Test
    void rejectsDocumentThatHasNotCompletedParsingBeforeDelete() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.PENDING);
        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() ->
                service.replaceChunks(DOCUMENT_ID, List.of())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱이 완료된 문서만");

        verify(chunkRepository, never())
                .deleteAllByDisclosureDocumentId(any());
    }

    @Test
    void rejectsChunkFromAnotherGeneratorBeforeDelete() {
        DisclosureDocument document = completedDocument();
        GeneratedDisclosureChunk generatedChunk = generatedChunk(
                "other-generator",
                "other-version"
        );
        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() ->
                service.replaceChunks(
                        DOCUMENT_ID,
                        List.of(generatedChunk)
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 정책과 다른 생성기 정보");

        verify(chunkRepository, never())
                .deleteAllByDisclosureDocumentId(any());
    }

    private DisclosureDocument completedDocument() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.COMPLETED);
        return document;
    }

    private GeneratedDisclosureChunk generatedChunk(
            String generatorName,
            String generatorVersion
    ) {
        GeneratedDisclosureChunk chunk =
                mock(GeneratedDisclosureChunk.class);
        when(chunk.generatorName()).thenReturn(generatorName);
        when(chunk.generatorVersion()).thenReturn(generatorVersion);
        when(chunk.chunkSequenceNo()).thenReturn(1);
        return chunk;
    }

    private DisclosureChunk chunkEntityWithSourceCount(int sourceCount) {
        DisclosureChunk chunk = mock(DisclosureChunk.class);
        List<DisclosureChunkSource> sources =
                java.util.stream.IntStream.range(0, sourceCount)
                        .mapToObj(index -> mock(DisclosureChunkSource.class))
                        .toList();
        when(chunk.getSources()).thenReturn(sources);
        return chunk;
    }
}
