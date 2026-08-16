package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XmlParsingPersistenceBatchServiceTest {

    @TempDir
    Path temporaryDirectory;

    private DisclosureDocumentRepository documentRepository;
    private DisclosurePathResolver pathResolver;
    private DisclosureDocumentParsingService parsingService;
    private XmlParsingPersistenceBatchService batchService;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        pathResolver = mock(DisclosurePathResolver.class);
        parsingService = mock(DisclosureDocumentParsingService.class);

        batchService = new XmlParsingPersistenceBatchService(
                documentRepository,
                pathResolver,
                parsingService
        );
    }

    @Test
    void pendingDartXmlDocumentsArePersistedFromFirstPage()
            throws IOException {
        UUID documentId = UUID.randomUUID();
        Path sourceFile = createSourceFile("test.xml");
        DisclosureDocument document = createDocumentMock(
                documentId,
                sourceFile
        );

        when(documentRepository
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, 10)
                ))
                .thenReturn(new SliceImpl<>(List.of(document)));

        when(parsingService.parseAndStore(documentId, sourceFile))
                .thenReturn(
                        new DisclosureParsingPersistenceResult(
                                documentId,
                                0,
                                0,
                                2,
                                5
                        )
                );

        XmlParsingPersistenceBatchResult result =
                batchService.persistNextChunk(10);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.savedSectionCount()).isEqualTo(2);
        assertThat(result.savedBlockCount()).isEqualTo(5);

        verify(documentRepository)
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, 10)
                );
        verify(parsingService).parseAndStore(documentId, sourceFile);
    }

    @Test
    void oneDocumentFailureIsRecordedInResultWithoutStoppingBatch()
            throws IOException {
        UUID failedDocumentId = UUID.randomUUID();
        UUID successDocumentId = UUID.randomUUID();
        Path failedFile = createSourceFile("failed.xml");
        Path successFile = createSourceFile("success.xml");

        DisclosureDocument failedDocument = createDocumentMock(
                failedDocumentId,
                failedFile
        );
        DisclosureDocument successDocument = createDocumentMock(
                successDocumentId,
                successFile
        );

        when(documentRepository
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, 2)
                ))
                .thenReturn(
                        new SliceImpl<>(
                                List.of(failedDocument, successDocument)
                        )
                );

        when(parsingService.parseAndStore(
                failedDocumentId,
                failedFile
        )).thenThrow(new IllegalStateException("파싱 실패"));

        when(parsingService.parseAndStore(
                successDocumentId,
                successFile
        )).thenReturn(
                new DisclosureParsingPersistenceResult(
                        successDocumentId,
                        0,
                        0,
                        1,
                        3
                )
        );

        XmlParsingPersistenceBatchResult result =
                batchService.persistNextChunk(2);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.savedSectionCount()).isEqualTo(1);
        assertThat(result.savedBlockCount()).isEqualTo(3);
        assertThat(result.rows().getFirst().errorMessage())
                .contains("IllegalStateException: 파싱 실패");

        verify(parsingService).parseAndStore(
                successDocumentId,
                successFile
        );
    }

    @Test
    void invalidChunkSizeIsRejected() {
        assertThatThrownBy(() -> batchService.persistNextChunk(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize는 1~100");

        assertThatThrownBy(() -> batchService.persistNextChunk(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize는 1~100");
    }

    @Test
    void sourcePathFailureIsMarkedAsFailed() {
        UUID documentId = UUID.randomUUID();
        Path missingFile = temporaryDirectory
                .resolve("missing.xml")
                .toAbsolutePath()
                .normalize();

        DisclosureDocument document = createDocumentMock(
                documentId,
                missingFile
        );

        when(documentRepository
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, 1)
                ))
                .thenReturn(new SliceImpl<>(List.of(document)));

        XmlParsingPersistenceBatchResult result =
                batchService.persistNextChunk(1);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().errorMessage())
                .contains("원문 파일이 존재하지 않거나");

        verify(parsingService).markFailed(
                org.mockito.ArgumentMatchers.eq(documentId),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
    }

    @Test
    void emptyPendingResultReturnsAnEmptyBatch() {
        when(documentRepository
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, 50)
                ))
                .thenReturn(new SliceImpl<>(List.of()));

        XmlParsingPersistenceBatchResult result =
                batchService.persistNextChunk(50);

        assertThat(result.totalCount()).isZero();
        assertThat(result.hasFailures()).isFalse();
    }

    private Path createSourceFile(String fileName) throws IOException {
        Path sourceFile = temporaryDirectory.resolve(fileName);
        Files.writeString(sourceFile, "<DOCUMENT></DOCUMENT>");
        return sourceFile.toAbsolutePath().normalize();
    }

    private DisclosureDocument createDocumentMock(
            UUID documentId,
            Path sourceFile
    ) {
        Disclosure disclosure = mock(Disclosure.class);
        DisclosureDocument document = mock(DisclosureDocument.class);
        String relativePath = "periodic/" + sourceFile.getFileName();

        when(disclosure.getReceiptNo()).thenReturn("20240101000001");
        when(disclosure.getManifestPath()).thenReturn("periodic");
        when(document.getId()).thenReturn(documentId);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(document.getFileName())
                .thenReturn(sourceFile.getFileName().toString());
        when(document.getNormalizedRelativePath())
                .thenReturn(relativePath);
        when(pathResolver.resolveDirectory("periodic"))
                .thenReturn(temporaryDirectory);
        when(pathResolver.toDatasetRelativePath(sourceFile))
                .thenReturn(relativePath);
        when(pathResolver.normalizeRelativePath(relativePath))
                .thenReturn(relativePath);

        return document;
    }
}
