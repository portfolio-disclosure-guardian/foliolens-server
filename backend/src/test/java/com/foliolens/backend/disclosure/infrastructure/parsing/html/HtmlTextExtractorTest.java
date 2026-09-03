package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextExtractorTest {

    private final HtmlTextExtractor extractor = new HtmlTextExtractor();

    @Test
    void preservesLineBreaksAndRemovesInvisibleText() {
        Element cell = Jsoup.parseBodyFragment("""
                <table><tr><td>
                  투자 <span>금액</span><br>100억원
                  <div>증설 목적</div>
                  <span class="noprint">인쇄 제외</span>
                  <span style="display: none">숨김</span>
                </td></tr></table>
                """).selectFirst("td");

        String text = extractor.extract(cell);

        assertThat(text).isEqualTo("투자 금액\n100억원\n증설 목적");
    }

    @Test
    void excludesNestedTableTextFromParentCell() {
        Element cell = Jsoup.parseBodyFragment("""
                <table><tr><td>
                  <div>표 앞 문맥</div>
                  <table><tr><td>중첩 표 값</td></tr></table>
                  <div>표 뒤 문맥</div>
                </td></tr></table>
                """).selectFirst("td");

        assertThat(extractor.extractExcludingNestedTables(cell))
                .isEqualTo("표 앞 문맥\n표 뒤 문맥");
        assertThat(extractor.extract(cell)).contains("중첩 표 값");
    }

    @Test
    void returnsNullForWhitespaceOnlyElement() {
        Element element = Jsoup.parseBodyFragment("<div> &nbsp; </div>")
                .selectFirst("div");

        assertThat(extractor.extract(element)).isNull();
    }
}
