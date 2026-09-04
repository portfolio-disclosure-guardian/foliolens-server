package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HtmlParsingPersistenceBatchServiceTest {
    @Test void selectsOnlyPendingHtmlAndContinuesAfterMissingFile() {
        var repository = mock(DisclosureDocumentRepository.class);
        var files = mock(DisclosureSourceFileResolver.class);
        var parsing = mock(DisclosureDocumentParsingService.class);
        var first = mock(DisclosureDocument.class);
        var second = mock(DisclosureDocument.class);
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        when(first.getId()).thenReturn(firstId);
        when(second.getId()).thenReturn(secondId);
        when(repository.findHtmlParsingTargets("신규시설투자등",
                DisclosureDocumentParseStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(List.of(first, second)));
        var error = new IllegalArgumentException("원문 없음");
        when(files.resolve(first)).thenThrow(error);
        var path = Path.of("second.xml");
        when(files.resolve(second)).thenReturn(path);
        when(parsing.parseAndStore(secondId, path)).thenReturn(
                new DisclosureParsingPersistenceResult(secondId, 0, 0, 2, 4));
        var result = new HtmlParsingPersistenceBatchService(repository, files, parsing)
                .persistNextBatch(10, "신규시설투자등");
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).containsKey(firstId);
        assertThat(result.savedBlockCount()).isEqualTo(4);
        verify(parsing).markFailed(firstId, error);
        verify(parsing).parseAndStore(secondId, path);
    }
}
