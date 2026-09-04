package com.foliolens.backend.disclosure.infrastructure.html;

import com.foliolens.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlSourceReaderTest {

    private final HtmlSourceReader reader = new HtmlSourceReader(
            new HtmlSourceFileValidator()
    );

    @Test
    void readsUtf8BytesEvenWhenMetaDeclaresEucKr(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path sourceFile = tempDirectory.resolve("disclosure.xml");
        String html = "<meta charset='euc-kr'><div>신규 시설투자</div>";
        Files.writeString(sourceFile, html, StandardCharsets.UTF_8);

        DecodedHtmlSource result = reader.read(sourceFile);

        assertThat(result.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(result.content()).contains("신규 시설투자");
        assertThat(result.sourceFile()).isEqualTo(
                sourceFile.toAbsolutePath().normalize()
        );
    }

    @Test
    void fallsBackToMs949WhenUtf8DecodingFails(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path sourceFile = tempDirectory.resolve("legacy.html");
        String html = "<html><body>투자목적</body></html>";
        Files.write(sourceFile, html.getBytes(Charset.forName("MS949")));

        DecodedHtmlSource result = reader.read(sourceFile);

        assertThat(result.charset()).isEqualTo(Charset.forName("MS949"));
        assertThat(result.content()).contains("투자목적");
    }

    @Test
    void removesUtf8Bom(@TempDir Path tempDirectory) throws Exception {
        Path sourceFile = tempDirectory.resolve("bom.html");
        Files.writeString(
                sourceFile,
                "\uFEFF<html><body>본문</body></html>",
                StandardCharsets.UTF_8
        );

        DecodedHtmlSource result = reader.read(sourceFile);

        assertThat(result.content()).startsWith("<html>");
    }

    @Test
    void rejectsEmptyFile(@TempDir Path tempDirectory) throws Exception {
        Path sourceFile = tempDirectory.resolve("empty.html");
        Files.createFile(sourceFile);

        assertThatThrownBy(() -> reader.read(sourceFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비어 있습니다");
    }
}
