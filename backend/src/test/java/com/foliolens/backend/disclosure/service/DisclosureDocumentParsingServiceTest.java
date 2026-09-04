package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.validation.HtmlParsingValidator;
import com.foliolens.backend.disclosure.infrastructure.persistence.*;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisclosureDocumentParsingServiceTest {
    @Test void xmlStillUsesSelectedParserAndVersion() {
        var repository = mock(DisclosureDocumentRepository.class);
        var router = mock(DisclosureDocumentParserRouter.class);
        var store = mock(DisclosureParsingPersistenceService.class);
        var failures = mock(DisclosureParsingFailureRecorder.class);
        var validator = mock(HtmlParsingValidator.class);
        var document = mock(DisclosureDocument.class);
        var parser = mock(DartXmlDisclosureParser.class);
        UUID id = UUID.randomUUID();
        Path file = Path.of("sample.xml");
        var parsed = new ParsedDisclosureDocument("sample.xml", "사업보고서", List.of(), List.of());
        var stored = new DisclosureParsingPersistenceResult(id, 0, 0, 0, 0);
        when(repository.findWithDisclosureById(id)).thenReturn(Optional.of(document));
        when(router.select(document)).thenReturn(parser);
        when(parser.parserName()).thenReturn("DartXmlDisclosureParser");
        when(parser.parserVersion()).thenReturn("1.1.0");
        when(parser.parse(file)).thenReturn(parsed);
        when(store.replaceParsedResult(id, parsed, "DartXmlDisclosureParser", "1.1.0")).thenReturn(stored);
        var service = new DisclosureDocumentParsingService(router, repository, store, failures, validator);
        assertThat(service.parseAndStore(id, file)).isSameAs(stored);
        verifyNoInteractions(validator, failures);
    }

    @Test void recordsRoutingFailureWithoutCallingAnyParserOrStore() {
        var repository = mock(DisclosureDocumentRepository.class);
        var router = mock(DisclosureDocumentParserRouter.class);
        var store = mock(DisclosureParsingPersistenceService.class);
        var failures = mock(DisclosureParsingFailureRecorder.class);
        var validator = mock(HtmlParsingValidator.class);
        var document = mock(DisclosureDocument.class);
        UUID id = UUID.randomUUID();
        var error = new IllegalArgumentException("PDF 미지원");
        when(repository.findWithDisclosureById(id)).thenReturn(Optional.of(document));
        when(router.select(document)).thenThrow(error);
        var service = new DisclosureDocumentParsingService(router, repository, store, failures, validator);
        assertThatThrownBy(() -> service.parseAndStore(id, Path.of("a.pdf"))).isSameAs(error);
        verify(failures).markFailed(id, "DisclosureDocumentParserRouter", "1.0.0", error);
        verifyNoInteractions(store, validator);
    }
}
