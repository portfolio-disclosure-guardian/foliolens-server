package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HtmlDisclosureLinkExtractorTest {
    @Test void keepsDartAndKrxIdentifiersSeparateAndDoesNotExecuteLinks() {
        var dom = Jsoup.parse("""
                <div>
                <a href="https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20240424800596">DART 공시</a>
                <a href="javascript:alert(1)">실행 불가</a>
                <a href="https://example.com/?rcpNo=20240424800596">외부</a>
                <span style="display:none"><a href="https://dart.fss.or.kr/?rcpNo=20240424800596">숨김</a></span>
                </div>
                """, "", Parser.htmlParser().setTrackPosition(true));
        var extractor = new HtmlDisclosureLinkExtractor(new HtmlTextExtractor(), new HtmlSourceLocationResolver());
        var links = extractor.extract(dom.body());
        assertThat(links).hasSize(1);
        assertThat(links.getFirst().dartReceiptNo()).isEqualTo("20240424800596");
        assertThat(links.getFirst().krxRcpNo()).isNull();
    }
}
