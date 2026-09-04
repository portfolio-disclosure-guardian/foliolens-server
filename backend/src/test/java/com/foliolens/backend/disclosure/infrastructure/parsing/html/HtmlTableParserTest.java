package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureImage;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlTableParserTest {

    private final HtmlTableParser tableParser = new HtmlTableParser(
            new HtmlTextExtractor(),
            new HtmlSourceLocationResolver()
    );

    @Test
    void parsesPhysicalRowsSpansNestedTableAndImage() {
        Document document = parseWithPositions("""
                <html><body>
                <table>
                  <tbody>
                    <tr>
                      <th rowspan="2">2. 투자내역</th>
                      <td>투자금액(원)</td>
                      <td colspan="2">1000000000</td>
                    </tr>
                    <tr>
                      <td>기타<br>설명</td>
                      <td>
                        <div>표 앞 문맥</div>
                        <table><tr><td>중첩 값</td></tr></table>
                        <div>표 뒤 문맥</div>
                        <img src="chart.png" alt="투자 도표"
                             width="615" height="834" align="center">
                      </td>
                    </tr>
                  </tbody>
                </table>
                </body></html>
                """);
        Element rootTable = document.selectFirst("table");

        ParsedDisclosureTable result = tableParser.parse(rootTable, 0);

        assertThat(result.order()).isZero();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.sourceLineStart()).isPositive();
        assertThat(result.sourceLineEnd())
                .isGreaterThanOrEqualTo(result.sourceLineStart());

        ParsedDisclosureTableCell header = result.rows().get(0).cells().get(0);
        assertThat(header.type())
                .isEqualTo(ParsedDisclosureTableCellType.HEADER);
        assertThat(header.rowSpan()).isEqualTo(2);
        assertThat(header.colSpan()).isEqualTo(1);

        ParsedDisclosureTableCell amount = result.rows().get(0).cells().get(2);
        assertThat(amount.colSpan()).isEqualTo(2);
        assertThat(amount.text()).isEqualTo("1000000000");

        ParsedDisclosureTableCell detail = result.rows().get(1).cells().get(1);
        assertThat(detail.text()).isEqualTo("표 앞 문맥\n표 뒤 문맥");
        assertThat(detail.nestedTables()).hasSize(1);
        assertThat(detail.nestedTables().getFirst().order()).isEqualTo(1);
        assertThat(detail.nestedTables().getFirst().rows()).hasSize(1);
        assertThat(detail.nestedTables().getFirst()
                .rows().getFirst().cells().getFirst().text())
                .isEqualTo("중첩 값");

        assertThat(detail.images()).hasSize(1);
        ParsedDisclosureImage image = detail.images().getFirst();
        assertThat(image.fileName()).isEqualTo("chart.png");
        assertThat(image.caption()).isEqualTo("투자 도표");
        assertThat(image.width()).isEqualTo(615);
        assertThat(image.height()).isEqualTo(834);
        assertThat(image.alignment()).isEqualTo("CENTER");
    }

    @Test
    void doesNotCountNestedRowsAsRootRows() {
        Document document = parseWithPositions("""
                <table>
                  <tr><td><table><tr><td>중첩</td></tr></table></td></tr>
                </table>
                """);

        ParsedDisclosureTable result = tableParser.parse(
                document.selectFirst("table"),
                0
        );

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().cells()).hasSize(1);
        assertThat(result.rows().getFirst().cells().getFirst().nestedTables())
                .hasSize(1);
    }

    @Test
    void rejectsInvalidSpanWithSourceLine() {
        Document document = parseWithPositions(
                "<table><tr><td rowspan='0'>값</td></tr></table>"
        );

        assertThatThrownBy(() -> tableParser.parse(
                document.selectFirst("table"),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROWSPAN")
                .hasMessageContaining("line=1");
    }

    private Document parseWithPositions(String html) {
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        return Jsoup.parse(html, "", parser);
    }
}
