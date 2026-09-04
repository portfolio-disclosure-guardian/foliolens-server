package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.parsing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class DartHtmlDisclosureParserTest {
    @Test void recognizesPlainSpanContractTitleAndUsesItAsDocumentName() throws Exception {
        var result = HtmlParserTestSupport.validator().validate(HtmlParserTestSupport.fixture("contract-original.xml"));
        var document = result.document();
        assertThat(document.documentName()).isEqualTo("단일판매ㆍ공급계약 체결");
        assertThat(document.sections()).extracting(ParsedDisclosureSection::title)
                .containsExactly("단일판매ㆍ공급계약 체결");
        assertThat(document.preambleBlocks()).isEmpty();
        var section = document.sections().getFirst();
        assertThat(section.sourceLineStart()).isEqualTo(6);
        assertThat(section.blocks()).hasSize(1);
        var table = section.blocks().getFirst().table();
        assertThat(table.sourceLineStart()).isEqualTo(8);
        assertThat(table.rows().get(2).cells().getLast().text()).isEqualTo("110,276,250,400");
        assertThat(table.rows().get(2).cells().getFirst().rowSpan()).isEqualTo(2);
        assertThat(table.rows().getLast().cells().getLast().text()).isEqualTo("공급 일정\n세부 조건");
    }

    @Test void separatesPlainSpanContractBodyFromCorrectionTables() throws Exception {
        var result = HtmlParserTestSupport.validator().validate(HtmlParserTestSupport.fixture("contract-correction.xml"));
        var document = result.document();
        assertThat(document.documentName()).isEqualTo("단일판매ㆍ공급계약 체결");
        assertThat(document.sections()).extracting(ParsedDisclosureSection::title)
                .containsExactly("정정신고(보고)", "단일판매ㆍ공급계약 체결");
        assertThat(document.sections().getFirst().blocks()).hasSize(3);
        assertThat(document.sections().getLast().blocks()).hasSize(1);
        assertThat(result.metrics().tableCount()).isEqualTo(4);
        assertThat(document.sections().getFirst().blocks().get(1).table().rows().getLast().cells())
                .extracting(ParsedDisclosureTableCell::text)
                .containsExactly("계약금액(원)", "100,000,000", "120,000,000");
        var body = document.sections().getLast().blocks().getFirst().table();
        assertThat(body.rows().getFirst().cells().getLast().text()).isEqualTo("120,000,000");
        assertThat(document.sections().getLast().sourceLineStart())
                .isGreaterThan(document.sections().getFirst().sourceLineEnd());
        assertThat(document.preambleBlocks()).isEmpty();
    }

    @Test void acceptsNumericBoldWeightAndIgnoresHiddenAndCellTitles(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("styled.xml");
        Files.writeString(file, """
                <html><body><div class="xforms">
                <span style="font-weight:bold;text-align:center;display:none">숨김 제목</span>
                <span style="FONT-WEIGHT: 700; TEXT-ALIGN: CENTER;">단일판매ㆍ공급계약 체결</span>
                <span class="noprint">표시되지 않는 안내</span>
                <table><tr><td><span style="font-weight:bold;text-align:center">셀 강조</span>
                <table><tr><td>중첩 표 내용</td></tr></table></td></tr></table>
                </div></body></html>
                """);
        var result = HtmlParserTestSupport.validator().validate(file);
        assertThat(result.document().sections()).extracting(ParsedDisclosureSection::title)
                .containsExactly("단일판매ㆍ공급계약 체결");
        assertThat(result.metrics().tableCount()).isEqualTo(2);
        assertThat(result.document().sections().getFirst().blocks().getFirst().table()
                .rows().getFirst().cells().getFirst().text()).isEqualTo("셀 강조");
    }

    @Test void doesNotPromoteStyledNoteWithoutAdjacentTable(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("note.xml");
        Files.writeString(file, """
                <html><body><div class="xforms">
                <span style="font-weight:bold;text-align:center">참고 문구</span>
                <p>중간 설명</p>
                <h2>계약 본문</h2><table><tr><td>계약금액</td><td>100</td></tr></table>
                </div></body></html>
                """);
        var result = HtmlParserTestSupport.validator().validate(file).document();
        assertThat(result.sections()).extracting(ParsedDisclosureSection::title).containsExactly("계약 본문");
        assertThat(result.preambleBlocks()).extracting(ParsedDisclosureBlock::content)
                .containsExactly("참고 문구", "중간 설명");
    }

    @Test void separatesCorrectionAndCurrentBodyWithoutDroppingTables() throws Exception {
        var validated = HtmlParserTestSupport.validator().validate(HtmlParserTestSupport.fixture("facility-correction.xml"));
        var result = validated.document();
        assertThat(result.sections()).extracting(ParsedDisclosureSection::title)
                .containsExactly("정정신고(보고)", "신규 시설투자 등");
        assertThat(result.preambleBlocks()).isEmpty();
        assertThat(validated.metrics().tableCount()).isEqualTo(4);
        assertThat(result.sections().get(0).blocks()).hasSize(3);
        var correction = result.sections().get(0).blocks().get(1).table();
        assertThat(correction.rows().get(2).cells()).extracting(ParsedDisclosureTableCell::text)
                .containsExactly("2. 투자내역-투자금액", "28,178,420,000", "28,533,520,000");
        var body = result.sections().get(1).blocks().getFirst().table();
        assertThat(body.rows().getFirst().cells().getLast().text()).isEqualTo("28,533,520,000");
        assertThat(body.rows().getFirst().cells().getFirst().rowSpan()).isEqualTo(3);
        assertThat(body.rows().get(3).cells().getLast().text()).isEqualTo("사옥 및 제조시설\n신축공사");
        assertThat(body.sourceLineStart()).isGreaterThan(correction.sourceLineEnd());
        assertThat(result.sections().get(1).blocks().getFirst().order())
                .isGreaterThan(result.sections().get(0).blocks().getLast().order());
        assertThat(result.relatedLinks()).hasSize(1);
        var link = result.relatedLinks().getFirst();
        assertThat(link.dartReceiptNo()).isNull();
        assertThat(link.krxAcptNo()).isEqualTo("20240430000348");
        assertThat(link.krxRcpNo()).isEqualTo("20240430000348");
        assertThat(link.sourceLineStart()).isPositive();
    }

    @Test void parsesOriginalXmlExtensionAsHtml() throws Exception {
        var result = HtmlParserTestSupport.validator().validate(HtmlParserTestSupport.fixture("facility-original.xml"));
        assertThat(result.document().sections()).hasSize(1);
        assertThat(result.metrics().tableCount()).isEqualTo(1);
        assertThat(result.document().sections().getFirst().blocks().getFirst().table()
                .rows().getFirst().cells().getLast().text()).isEqualTo("5,296,200,000,000");
    }

    @Test void rejectsNonXformsHtmlAndReplacementCharacters(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("bad.xml");
        Files.writeString(file, "<html><body>error page</body></html>");
        assertThatThrownBy(() -> HtmlParserTestSupport.parser().parse(file))
                .hasMessageContaining("XForms");
        Files.writeString(file, "<html><body><div class='xforms'>\uFFFD</div></body></html>");
        assertThatThrownBy(() -> HtmlParserTestSupport.parser().parse(file))
                .hasMessageContaining("대체문자");
    }
}
