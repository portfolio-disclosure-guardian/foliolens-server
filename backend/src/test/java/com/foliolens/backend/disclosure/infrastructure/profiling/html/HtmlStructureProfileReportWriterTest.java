package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HtmlStructureProfileReportWriterTest {

    @Test
    void writesEveryColumnWithoutShiftingValues(
            @TempDir Path tempDirectory
    ) throws Exception {
        Disclosure disclosure = mock(Disclosure.class);
        when(disclosure.getSourceDocId()).thenReturn("exchange_20240424800596");
        when(disclosure.getReceiptNo()).thenReturn("20240424800596");
        when(disclosure.getSourceGroup()).thenReturn(
                DisclosureSourceGroup.EXCHANGE
        );
        when(disclosure.getRawSubtype()).thenReturn("신규시설투자등");
        when(disclosure.getReportName()).thenReturn("신규 시설투자 공시");
        when(disclosure.isCorrection()).thenReturn(false);

        UUID documentId = UUID.randomUUID();
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(document.getFileName()).thenReturn("20240424800596.xml");
        when(document.getDocumentRole()).thenReturn(DisclosureDocumentRole.MAIN);
        when(document.getContentFormat()).thenReturn(
                DisclosureDocumentContentFormat.HTML
        );
        when(document.getRelativePath()).thenReturn(
                "exchange/20240424800596/20240424800596.xml"
        );
        when(document.getDocumentName()).thenReturn("신규시설투자등");

        HtmlStructureProfile profile = new HtmlStructureProfile(
                "20240424800596.xml",
                "HTML",
                "신규 시설투자 공시",
                "UTF-8",
                "euc-kr",
                1234,
                8,
                Map.of("HTML", 1L, "TABLE", 2L, "TR", 3L, "TD", 4L),
                Map.of("xforms", 1L, "xforms_input", 2L),
                1,
                1,
                2,
                2,
                1,
                1,
                2,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                0,
                null
        );
        HtmlStructureProfileRow row = HtmlStructureProfileRow.success(
                document,
                profile,
                37
        );
        Instant startedAt = Instant.parse("2026-09-02T00:00:00Z");
        HtmlStructureProfileBatchResult result =
                new HtmlStructureProfileBatchResult(
                        startedAt,
                        startedAt.plusMillis(40),
                        List.of(row)
                );

        Path reportPath = new HtmlStructureProfileReportWriter().write(
                tempDirectory.resolve("html-profile.csv"),
                result
        );

        String csv = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        try (CSVParser parser = CSVParser.parse(
                csv.substring(1),
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            CSVRecord record = parser.getRecords().getFirst();
            assertThat(record.get("disclosure_document_id"))
                    .isEqualTo(documentId.toString());
            assertThat(record.get("raw_subtype")).isEqualTo("신규시설투자등");
            assertThat(record.get("max_table_depth")).isEqualTo("2");
            assertThat(record.get("max_col_span")).isEqualTo("7");
            assertThat(record.get("parser_error_count")).isEqualTo("0");
            assertThat(record.get("elapsed_millis")).isEqualTo("37");
            assertThat(record.get("status")).isEqualTo("SUCCESS");
            assertThat(record.get("error_message")).isEmpty();
        }
    }
}
