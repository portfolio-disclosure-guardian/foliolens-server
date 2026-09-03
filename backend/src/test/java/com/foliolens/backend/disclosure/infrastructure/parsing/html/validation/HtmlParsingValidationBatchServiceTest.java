package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.HtmlParserTestSupport;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HtmlParsingValidationBatchServiceTest {
    @Test void continuesAfterFailureAndDoesNotWriteDatabase() throws Exception {
        var repository = mock(DisclosureDocumentRepository.class);
        var files = mock(DisclosureSourceFileResolver.class);
        var validator = mock(HtmlParsingValidator.class);
        var first = document("first.xml");
        var second = document("second.xml");
        var page = PageRequest.of(0, 50);
        when(repository.countHtmlParsingTargets("신규시설투자등")).thenReturn(2L);
        when(repository.findHtmlParsingTargets("신규시설투자등", null, page))
                .thenReturn(new SliceImpl<>(List.of(first, second)));
        Path firstFile = Path.of("first.xml");
        Path secondFile = Path.of("second.xml");
        when(files.resolve(first)).thenReturn(firstFile);
        when(files.resolve(second)).thenReturn(secondFile);
        when(validator.validate(firstFile)).thenThrow(new IllegalArgumentException("잘못된 원문"));
        when(validator.validate(secondFile)).thenReturn(HtmlParserTestSupport.validator()
                .validate(HtmlParserTestSupport.fixture("facility-original.xml")));
        var result = new HtmlParsingValidationBatchService(repository, files, validator)
                .validate(0, 50, "신규시설투자등");
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().errorMessage()).contains("잘못된 원문");
        verify(repository).countHtmlParsingTargets("신규시설투자등");
        verify(repository).findHtmlParsingTargets("신규시설투자등", null, page);
        verifyNoMoreInteractions(repository);
    }

    private DisclosureDocument document(String name) {
        var document = mock(DisclosureDocument.class);
        var disclosure = mock(Disclosure.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getFileName()).thenReturn(name);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(disclosure.getReceiptNo()).thenReturn("20240424800596");
        return document;
    }
}
