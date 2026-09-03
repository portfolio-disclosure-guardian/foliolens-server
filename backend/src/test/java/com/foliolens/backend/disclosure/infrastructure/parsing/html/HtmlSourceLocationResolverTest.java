package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSourceLocationResolverTest {

    private final HtmlSourceLocationResolver resolver =
            new HtmlSourceLocationResolver();

    @Test
    void resolvesTrackedStartAndEndLines() {
        Document document = parseWithPositions("""
                <html>
                <body>
                <table>
                  <tr>
                    <td>투자금액</td>
                  </tr>
                </table>
                </body>
                </html>
                """);
        Element table = document.selectFirst("table");
        Element cell = document.selectFirst("td");

        HtmlSourceLineRange tableRange = resolver.resolve(table);
        HtmlSourceLineRange cellRange = resolver.resolve(cell);

        assertThat(tableRange.startLine()).isEqualTo(3);
        assertThat(tableRange.endLine()).isEqualTo(7);
        assertThat(cellRange.startLine()).isEqualTo(5);
        assertThat(cellRange.endLine()).isEqualTo(5);
        assertThat(resolver.startOffset(cell)).isPositive();
    }

    @Test
    void returnsMinusOneWhenPositionTrackingIsDisabled() {
        Document document = Jsoup.parse("<table><tr><td>값</td></tr></table>");

        HtmlSourceLineRange range = resolver.resolve(
                document.selectFirst("table")
        );

        assertThat(range).isEqualTo(HtmlSourceLineRange.untracked());
        assertThat(resolver.startOffset(document.selectFirst("table")))
                .isEqualTo(Integer.MAX_VALUE);
    }

    private Document parseWithPositions(String html) {
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        return Jsoup.parse(html, "", parser);
    }
}
