package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureChunkPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkingBatchServiceTest {

    private DisclosureDocumentRepository documentRepository;
    private DisclosureDocumentChunkingService chunkingService;
    private DisclosureChunkingBatchService batchService;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        chunkingService = mock(DisclosureDocumentChunkingService.class);
        batchService = new DisclosureChunkingBatchService(
                documentRepository,
                chunkingService
        );
    }

    @Test
    void completedAndPendingDartXmlDocumentsAreChunkedFromFirstPage() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        DisclosureDocument first = document(firstId, 1);
        DisclosureDocument second = document(secondId, 2);

        when(documentRepository
                .findChunkingTargets(
                        DisclosureDocumentContentFormat.DART_XML,
                        null,
                        PageRequest.of(0, 2)
                )).thenReturn(new SliceImpl<>(List.of(first, second)));

        when(chunkingService.generateAndStore(firstId))
                .thenReturn(result(firstId, 1, 3, 5));
        when(chunkingService.generateAndStore(secondId))
                .thenReturn(result(secondId, 0, 4, 7));

        DisclosureChunkingBatchResult result =
                batchService.processNextBatch(2);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        assertThat(result.deletedChunkCount()).isEqualTo(1);
        assertThat(result.savedChunkCount()).isEqualTo(7);
        assertThat(result.savedSourceCount()).isEqualTo(12);
        assertThat(result.hasFailures()).isFalse();

        verify(chunkingService).generateAndStore(firstId);
        verify(chunkingService).generateAndStore(secondId);
    }

    @Test
    void oneFailureDoesNotStopFollowingDocument() {
        UUID failedId = UUID.randomUUID();
        UUID successId = UUID.randomUUID();
        DisclosureDocument failed = document(failedId, 1);
        DisclosureDocument success = document(successId, 2);
        RuntimeException failure = new RuntimeException(
                "상위 실패",
                new IllegalArgumentException("실제 청킹 실패")
        );

        when(documentRepository
                .findChunkingTargets(
                        DisclosureDocumentContentFormat.DART_XML,
                        null,
                        PageRequest.of(0, 2)
                )).thenReturn(new SliceImpl<>(List.of(failed, success)));

        when(chunkingService.generateAndStore(failedId))
                .thenThrow(failure);
        when(chunkingService.generateAndStore(successId))
                .thenReturn(result(successId, 0, 2, 3));

        DisclosureChunkingBatchResult result =
                batchService.processNextBatch(2);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.savedChunkCount()).isEqualTo(2);
        assertThat(result.savedSourceCount()).isEqualTo(3);
        assertThat(result.rows().getFirst().errorMessage())
                .isEqualTo("IllegalArgumentException: 실제 청킹 실패");

        verify(chunkingService).generateAndStore(successId);
    }

    @Test
    void invalidBatchSizeIsRejected() {
        assertThatThrownBy(() -> batchService.processNextBatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize는 1~100");

        assertThatThrownBy(() -> batchService.processNextBatch(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize는 1~100");
    }

    @Test
    void emptyPendingResultReturnsEmptyBatch() {
        when(documentRepository
                .findChunkingTargets(
                        DisclosureDocumentContentFormat.DART_XML,
                        null,
                        PageRequest.of(0, 10)
                )).thenReturn(new SliceImpl<>(List.of()));

        DisclosureChunkingBatchResult result =
                batchService.processNextBatch(10);

        assertThat(result.totalCount()).isZero();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void htmlSubtypeIsNormalizedAndOnlySelectedDocumentsAreProcessed() {
        UUID id = UUID.randomUUID();
        DisclosureDocument htmlDocument = document(id, 1);
        when(documentRepository.findChunkingTargets(
                DisclosureDocumentContentFormat.HTML, "신규시설투자등", PageRequest.of(0, 5)))
                .thenReturn(new SliceImpl<>(List.of(htmlDocument)));
        when(chunkingService.generateAndStore(id)).thenReturn(result(id, 0, 2, 2));

        var result = batchService.processNextBatch(5, DisclosureDocumentContentFormat.HTML, " 신규시설투자등 ");

        assertThat(result.successCount()).isEqualTo(1);
        verify(documentRepository).findChunkingTargets(
                DisclosureDocumentContentFormat.HTML, "신규시설투자등", PageRequest.of(0, 5));
        verify(chunkingService).generateAndStore(id);
    }

    @Test
    void emptySubtypeSelectsAllHtmlTypes() {
        when(documentRepository.findChunkingTargets(
                DisclosureDocumentContentFormat.HTML, null, PageRequest.of(0, 5)))
                .thenReturn(new SliceImpl<>(List.of()));
        assertThat(batchService.processNextBatch(5, DisclosureDocumentContentFormat.HTML, "  ").totalCount()).isZero();
    }

    @Test
    void unsupportedOrMissingFormatIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> batchService.processNextBatch(5, DisclosureDocumentContentFormat.UNKNOWN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DART_XML, HTML 또는 PDF");
        assertThatThrownBy(() -> batchService.processNextBatch(5, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(documentRepository, chunkingService);
    }

    private DisclosureDocument document(UUID documentId, int index) {
        Disclosure disclosure = mock(Disclosure.class);
        DisclosureDocument document = mock(DisclosureDocument.class);

        when(disclosure.getReceiptNo())
                .thenReturn("%014d".formatted(index));
        when(document.getId()).thenReturn(documentId);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(document.getFileName()).thenReturn("document-" + index + ".xml");

        return document;
    }

    private DisclosureChunkPersistenceResult result(
            UUID documentId,
            int deletedChunkCount,
            int savedChunkCount,
            int savedSourceCount
    ) {
        return new DisclosureChunkPersistenceResult(
                documentId,
                deletedChunkCount,
                savedChunkCount,
                savedSourceCount
        );
    }
}
