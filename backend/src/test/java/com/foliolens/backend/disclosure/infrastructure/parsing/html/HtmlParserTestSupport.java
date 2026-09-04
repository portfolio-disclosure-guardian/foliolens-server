package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.html.HtmlSourceFileValidator;
import com.foliolens.backend.disclosure.infrastructure.html.HtmlSourceReader;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.validation.HtmlParsingValidator;
import com.foliolens.backend.disclosure.infrastructure.parsing.validation.XmlParsingValidationMetricsCollector;
import com.foliolens.backend.disclosure.infrastructure.profiling.html.HtmlStructureProfiler;
import java.nio.file.Path;

public final class HtmlParserTestSupport {
    private HtmlParserTestSupport() {}
    public static DartHtmlDisclosureParser parser() {
        var locations = new HtmlSourceLocationResolver();
        var texts = new HtmlTextExtractor();
        return new DartHtmlDisclosureParser(new HtmlSourceReader(new HtmlSourceFileValidator()),
                locations, texts, new HtmlTableParser(texts, locations),
                new HtmlDisclosureLinkExtractor(texts, locations));
    }
    public static HtmlParsingValidator validator() {
        return new HtmlParsingValidator(parser(), new HtmlStructureProfiler(new HtmlSourceFileValidator()),
                new XmlParsingValidationMetricsCollector());
    }
    public static Path fixture(String name) throws Exception {
        return Path.of(HtmlParserTestSupport.class.getResource("/fixtures/html/" + name).toURI());
    }
}
