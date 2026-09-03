package com.foliolens.backend.disclosure.infrastructure.parsing;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.DartHtmlDisclosureParser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisclosureDocumentParserRouterTest {
    private final DartXmlDisclosureParser xml = mock(DartXmlDisclosureParser.class);
    private final DartHtmlDisclosureParser html = mock(DartHtmlDisclosureParser.class);
    private final DisclosureDocumentParserRouter router = new DisclosureDocumentParserRouter(xml, html);

    @Test void routesByContentFormatNotXmlExtension() {
        assertThat(router.select(document(DisclosureDocumentContentFormat.HTML, "exchange", DisclosureDocumentRole.MAIN)))
                .isSameAs(html);
        assertThat(router.select(document(DisclosureDocumentContentFormat.DART_XML, "major", DisclosureDocumentRole.MAIN)))
                .isSameAs(xml);
    }

    @Test void rejectsViewersUnknownPdfAndConflictingGroup() {
        assertThatThrownBy(() -> router.select(document(DisclosureDocumentContentFormat.HTML, "periodic", DisclosureDocumentRole.VIEWER)))
                .hasMessageContaining("뷰어");
        assertThatThrownBy(() -> router.select(document(DisclosureDocumentContentFormat.PDF, "periodic", DisclosureDocumentRole.MAIN)))
                .hasMessageContaining("지원하지");
        assertThatThrownBy(() -> router.select(document(DisclosureDocumentContentFormat.DART_XML, "exchange", DisclosureDocumentRole.MAIN)))
                .hasMessageContaining("충돌");
    }

    private DisclosureDocument document(DisclosureDocumentContentFormat format, String group, DisclosureDocumentRole role) {
        var document = mock(DisclosureDocument.class);
        var disclosure = mock(Disclosure.class);
        when(document.getContentFormat()).thenReturn(format);
        when(document.getDocumentRole()).thenReturn(role);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(disclosure.getSourceGroup()).thenReturn(DisclosureSourceGroup.fromValue(group));
        return document;
    }
}
