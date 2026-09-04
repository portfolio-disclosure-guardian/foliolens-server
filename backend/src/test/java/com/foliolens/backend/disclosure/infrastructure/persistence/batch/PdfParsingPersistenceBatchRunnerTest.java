package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.SliceImpl;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfParsingPersistenceBatchRunnerTest {
    private final DisclosureDocumentRepository repository = mock(DisclosureDocumentRepository.class);
    private final DisclosureSourceFileResolver files = mock(DisclosureSourceFileResolver.class);
    private final DisclosureDocumentParsingService parsing = mock(DisclosureDocumentParsingService.class);
    private final UUID id = UUID.randomUUID();

    @Test void selectsPendingPdfOnlyAndStopsAtLimit() {
        var document = target();
        var path = Path.of("test.pdf");
        when(files.resolve(document)).thenReturn(path);
        when(parsing.parseAndStore(id, path)).thenReturn(new DisclosureParsingPersistenceResult(id, 0, 0, 3, 2));
        new PdfParsingPersistenceBatchRunner(repository, files, parsing, 1).run(new DefaultApplicationArguments());
        verify(parsing).parseAndStore(id, path);
        verify(repository).findAllByContentFormatAndParseStatusOrderByIdAsc(
                eq(DisclosureDocumentContentFormat.PDF), eq(DisclosureDocumentParseStatus.PENDING), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void pathFailureIsRecordedAndStopsImmediately() {
        var document = target();
        var error = new IllegalArgumentException("hash mismatch");
        when(files.resolve(document)).thenThrow(error);
        new PdfParsingPersistenceBatchRunner(repository, files, parsing, 3).run(new DefaultApplicationArguments());
        verify(parsing).markFailed(id, error);
        verify(parsing, never()).parseAndStore(any(), any());
        verify(repository).findAllByContentFormatAndParseStatusOrderByIdAsc(any(), any(), any());
    }

    @Test void noPendingDocumentsDoesNothing() {
        when(repository.findAllByContentFormatAndParseStatusOrderByIdAsc(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of()));
        new PdfParsingPersistenceBatchRunner(repository, files, parsing, 3).run(new DefaultApplicationArguments());
        verifyNoInteractions(files, parsing);
        assertThatThrownBy(() -> new PdfParsingPersistenceBatchRunner(repository, files, parsing, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DisclosureDocument target() {
        var document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(id);
        when(repository.findAllByContentFormatAndParseStatusOrderByIdAsc(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(document)));
        return document;
    }
}
