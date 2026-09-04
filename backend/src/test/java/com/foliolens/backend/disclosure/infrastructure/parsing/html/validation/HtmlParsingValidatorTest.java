package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.infrastructure.parsing.html.HtmlParserTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlParsingValidatorTest {
    @TempDir Path directory;

    @Test void reportsMissingSectionWithActualMetrics() throws Exception {
        Path file = write("<span>알 수 없는 제목</span><table><tr><td>값</td></tr></table>");
        assertThatThrownBy(() -> HtmlParserTestSupport.validator().validate(file))
                .hasMessageContaining("섹션 제목 미인식")
                .hasMessageContaining("sections=0")
                .hasMessageContaining("preambleBlocks=2")
                .hasMessageContaining("tables=1")
                .hasMessageContaining("cells=1");
    }

    @Test void rejectsCorrectionOnlySectionEvenWhenTableCountsMatch() throws Exception {
        Path file = write("""
                <span>정정신고(보고)</span><table><tr><td>정정 내용</td></tr></table>
                <span>인식하지 못한 계약 제목</span><table><tr><td>계약 본문</td></tr></table>
                """);
        assertThatThrownBy(() -> HtmlParserTestSupport.validator().validate(file))
                .hasMessageContaining("정정 외 본문 섹션 누락")
                .hasMessageContaining("sections=1")
                .hasMessageContaining("tables=2");
    }

    @Test void rejectsEmptyBodySectionAfterCorrection() throws Exception {
        Path file = write("""
                <span>정정신고(보고)</span><table><tr><td>정정 내용</td></tr></table>
                <h2>계약 본문</h2>
                """);
        assertThatThrownBy(() -> HtmlParserTestSupport.validator().validate(file))
                .hasMessageContaining("본문 섹션에 표가 없습니다");
    }

    @Test void reportsMissingTableAndCellsSeparately() throws Exception {
        Path file = write("<h2>계약 본문</h2><p>표가 없는 본문</p>");
        assertThatThrownBy(() -> HtmlParserTestSupport.validator().validate(file))
                .hasMessageContaining("표 누락")
                .hasMessageContaining("표 셀 누락")
                .hasMessageContaining("tables=0")
                .hasMessageContaining("cells=0");
    }

    @Test void rejectsBodyBeforeCorrectionSection() throws Exception {
        Path file = write("""
                <h2>계약 본문</h2><table><tr><td>본문</td></tr></table>
                <span>정정신고(보고)</span><table><tr><td>정정 내용</td></tr></table>
                """);
        assertThatThrownBy(() -> HtmlParserTestSupport.validator().validate(file))
                .hasMessageContaining("섹션 순서 오류");
    }

    private Path write(String body) throws Exception {
        Path file = directory.resolve("validation.xml");
        Files.writeString(file, "<html><body><div class='xforms'>" + body + "</div></body></html>");
        return file;
    }
}
