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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureDocumentChunkingServiceTest {

    private static final UUID DOCUMENT_ID = new UUID(800, 1);

    private DisclosureDocumentRepository documentRepository;
    private DisclosureSectionRepository sectionRepository;
    private DisclosureContentBlockRepository blockRepository;
    private DisclosureChunkGenerator chunkGenerator;
    private DisclosureChunkPersistenceService persistenceService;
    private DisclosureChunkFailureRecorder failureRecorder;
    private DisclosureDocumentChunkingService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        sectionRepository = mock(DisclosureSectionRepository.class);
        blockRepository = mock(DisclosureContentBlockRepository.class);
        chunkGenerator = mock(DisclosureChunkGenerator.class);
        persistenceService = mock(DisclosureChunkPersistenceService.class);
        failureRecorder = mock(DisclosureChunkFailureRecorder.class);

        service = new DisclosureDocumentChunkingService(
                documentRepository,
                sectionRepository,
                blockRepository,
                chunkGenerator,
                persistenceService,
                failureRecorder
        );
    }

    @Test
    void generatesAndStoresChunksForCompletedDocument() {
        DisclosureDocument document = completedDocument();
        DisclosureSection section = mock(DisclosureSection.class);
        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        GeneratedDisclosureChunk generatedChunk =
                mock(GeneratedDisclosureChunk.class);
        DisclosureChunkPersistenceResult expected =
                new DisclosureChunkPersistenceResult(
                        DOCUMENT_ID,
                        2,
                        1,
                        1
                );

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        when(sectionRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(section));
        when(blockRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(block));
        when(chunkGenerator.generateChunks(
                DOCUMENT_ID,
                List.of(section),
                List.of(block)
        )).thenReturn(List.of(generatedChunk));
        when(persistenceService.replaceChunks(
                DOCUMENT_ID,
                List.of(generatedChunk)
        )).thenReturn(expected);

        DisclosureChunkPersistenceResult actual =
                service.generateAndStore(DOCUMENT_ID);

        assertThat(actual).isSameAs(expected);
        verify(failureRecorder, never())
                .markFailed(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void recordsFailureAndRethrowsGeneratorException() {
        DisclosureDocument document = completedDocument();
        DisclosureSection section = mock(DisclosureSection.class);
        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        RuntimeException failure = new IllegalStateException(
                "청크 생성 실패"
        );

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        when(sectionRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(section));
        when(blockRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of(block));
        when(chunkGenerator.generateChunks(
                DOCUMENT_ID,
                List.of(section),
                List.of(block)
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.generateAndStore(DOCUMENT_ID))
                .isSameAs(failure);

        verify(failureRecorder).markFailed(DOCUMENT_ID, failure);
        verify(persistenceService, never())
                .replaceChunks(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList()
                );
    }

    @Test
    void rejectsUnparsedDocumentWithoutRecordingProcessingFailure() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.PENDING);
        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.generateAndStore(DOCUMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱이 완료된 문서만");

        verify(failureRecorder, never())
                .markFailed(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
        verify(chunkGenerator, never())
                .generateChunks(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyList()
                );
    }

    @Test
    void rejectsMissingDocumentWithoutTryingToRecordIt() {
        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAndStore(DOCUMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("청크를 생성할 원문 문서");

        verify(failureRecorder, never())
                .markFailed(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    private DisclosureDocument completedDocument() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.COMPLETED);
        when(document.isChunkable()).thenReturn(true);
        return document;
    }
}
