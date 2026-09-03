package com.foliolens.backend.disclosure.infrastructure.parsing.pdf;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureBlockType;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class PdfTextDisclosureParserTest {
    @TempDir Path directory;
    private final PdfTextDisclosureParser parser = new PdfTextDisclosureParser();

    /** 테스트 전용 임시 PDF. 실제 원문 데이터와 DB에는 쓰지 않는다. */
    public static Path createPdf(Path file, List<String> pages) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                if (text.isEmpty()) continue;
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    stream.setLeading(12);
                    stream.newLineAtOffset(40, 750);
                    for (String line : text.split("\n")) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            pdf.save(file.toFile());
        }
        return file;
    }

    @Test void keepsPhysicalPagesWithoutInventingXmlLinesOrTables() throws Exception {
        var parsed = parser.parse(createPdf(directory.resolve("test.pdf"), List.of(
                "Revenue 100,000 KRW\nPeriod 2025", "", "Debt -200 KRW")));
        assertThat(parsed.pdfTextReport().pageCount()).isEqualTo(3);
        assertThat(parsed.pdfTextReport().noTextPages()).containsExactly(2);
        assertThat(parsed.pdfTextReport().tablesReconstructed()).isFalse();
        assertThat(parsed.pdfTextReport().ocrPerformed()).isFalse();
        assertThat(parsed.sections()).hasSize(3);
        assertThat(parsed.sections().get(1).blocks()).isEmpty();
        var first = parsed.sections().getFirst().blocks().getFirst();
        var last = parsed.sections().getLast().blocks().getFirst();
        assertThat(first.content()).contains("Revenue 100,000 KRW", "Period 2025");
        assertThat(first.type()).isEqualTo(ParsedDisclosureBlockType.PARAGRAPH);
        assertThat(first.sourceLineStart()).isEqualTo(-1);
        assertThat(first.sourceLineEnd()).isEqualTo(-1);
        assertThat(first.pdfPage().pageNumber()).isEqualTo(1);
        assertThat(last.pdfPage().pageNumber()).isEqualTo(3);
        assertThat(last.content()).contains("Debt -200 KRW");
    }

    @Test void rejectsEmptyOrBrokenPdfRatherThanStoringFakeSuccess() throws Exception {
        Path empty = createPdf(directory.resolve("empty.pdf"), List.of(""));
        assertThatThrownBy(() -> parser.parse(empty)).hasMessageContaining("추출 가능한");
        Path broken = directory.resolve("broken.pdf");
        Files.writeString(broken, "not a PDF");
        assertThatThrownBy(() -> parser.parse(broken)).hasMessageContaining("추출에 실패");
    }

    @Test void flagsSuspiciousTextButDoesNotDeleteIt() throws Exception {
        String text = "A\n".repeat(25) + "Revenue 100";
        assertThat(PdfTextDisclosureParser.isSuspicious(text)).isTrue();
        assertThat(PdfTextDisclosureParser.isSuspicious("Revenue 100\nDebt 200")).isFalse();
        assertThat(PdfTextDisclosureParser.isSuspicious("bad \uFFFD")).isTrue();
        var parsed = parser.parse(createPdf(directory.resolve("suspect.pdf"), List.of(text)));
        assertThat(parsed.pdfTextReport().suspiciousPages()).containsExactly(1);
        assertThat(parsed.sections().getFirst().blocks().getFirst().pdfPage().textExtractionSuspect()).isTrue();
        assertThat(parsed.sections().getFirst().blocks().getFirst().content()).contains("Revenue 100");
    }
}
