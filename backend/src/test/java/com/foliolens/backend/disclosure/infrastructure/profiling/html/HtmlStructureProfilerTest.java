package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import com.foliolens.backend.disclosure.infrastructure.html.HtmlSourceFileValidator;
import com.foliolens.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlStructureProfilerTest {

    private final HtmlStructureProfiler profiler = new HtmlStructureProfiler(
            new HtmlSourceFileValidator()
    );

    @Test
    void profilesLegacyXformsHtmlAndNestedTables(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path sourceFile = tempDirectory.resolve("20240424800596.xml");
        Files.writeString(
                sourceFile,
                """
                        <html>
                        <head>
                          <meta http-equiv="content-type" content="text/html; charset=euc-kr">
                          <title>신규 시설투자 공시</title>
                          <style>.xforms_input { color: black; }</style>
                        </head>
                        <body>
                          <div class="xforms">
                            <div class="xforms_title">투자 내역</div>
                            <table>
                              <tr>
                                <th rowspan="2">투자내역</th>
                                <th>투자금액(원)</th>
                                <td colspan="2" class="xforms_input">1000000000</td>
                              </tr>
                              <tr>
                                <td>세부내역</td>
                                <td>
                                  <table><tr><td>설비 증설</td></tr></table>
                                </td>
                              </tr>
                            </table>
                            <a href="https://example.com">근거 링크</a><br>
                            <img src="chart.png">
                          </div>
                        </body>
                        </html>
                        """,
                StandardCharsets.UTF_8
        );

        HtmlStructureProfile profile = profiler.profile(sourceFile);

        assertThat(profile.rootElementName()).isEqualTo("HTML");
        assertThat(profile.title()).isEqualTo("신규 시설투자 공시");
        assertThat(profile.decodedCharset()).isEqualTo("UTF-8");
        assertThat(profile.declaredCharset()).isEqualTo("euc-kr");
        assertThat(profile.xformsContainerCount()).isEqualTo(1);
        assertThat(profile.xformsTitleCount()).isEqualTo(1);
        assertThat(profile.xformsInputCount()).isEqualTo(1);
        assertThat(profile.tableCount()).isEqualTo(2);
        assertThat(profile.topLevelTableCount()).isEqualTo(1);
        assertThat(profile.nestedTableCount()).isEqualTo(1);
        assertThat(profile.maxTableDepth()).isEqualTo(2);
        assertThat(profile.rowSpanCellCount()).isEqualTo(1);
        assertThat(profile.maxRowSpan()).isEqualTo(2);
        assertThat(profile.colSpanCellCount()).isEqualTo(1);
        assertThat(profile.maxColSpan()).isEqualTo(2);
        assertThat(profile.lineBreakCount()).isEqualTo(1);
        assertThat(profile.anchorCount()).isEqualTo(1);
        assertThat(profile.anchorWithHrefCount()).isEqualTo(1);
        assertThat(profile.imageCount()).isEqualTo(1);
        assertThat(profile.countOf("TR")).isEqualTo(3);
        assertThat(profile.countOf("TD")).isEqualTo(4);
    }

    @Test
    void recordsInvalidSpanAttributesInsteadOfFailing(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path sourceFile = tempDirectory.resolve("invalid-span.html");
        Files.writeString(
                sourceFile,
                "<html><body><table><tr><td rowspan='x'>값</td>"
                        + "<td colspan='0'>값</td></tr></table></body></html>",
                StandardCharsets.UTF_8
        );

        HtmlStructureProfile profile = profiler.profile(sourceFile);

        assertThat(profile.invalidSpanAttributeCount()).isEqualTo(2);
        assertThat(profile.tableCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToMs949WhenUtf8DecodingFails(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path sourceFile = tempDirectory.resolve("legacy.xml");
        String html = "<html><head><meta content='text/html; charset=euc-kr'>"
                + "<title>시설투자</title></head><body>투자목적</body></html>";
        Files.write(sourceFile, html.getBytes(Charset.forName("MS949")));

        HtmlStructureProfile profile = profiler.profile(sourceFile);

        assertThat(profile.decodedCharset())
                .isEqualTo(Charset.forName("MS949").name());
        assertThat(profile.title()).isEqualTo("시설투자");
    }

    @Test
    void rejectsUnsupportedExtension(@TempDir Path tempDirectory)
            throws Exception {
        Path sourceFile = tempDirectory.resolve("disclosure.txt");
        Files.writeString(sourceFile, "<html></html>");

        assertThatThrownBy(() -> profiler.profile(sourceFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".xml 파일만 지원");
    }
}
