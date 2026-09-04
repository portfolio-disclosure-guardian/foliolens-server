package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 기존 구조 조사 CSV처럼 UTF-8 BOM과 명시적인 빈 필드로 Excel 열 밀림을 방지한다. */
@Component
public class HtmlParsingValidationReportWriter {
    public Path write(Path output, HtmlParsingValidationBatchResult result) {
        Path target = output.toAbsolutePath().normalize();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(
                "disclosure_document_id", "receipt_no", "file_name", "correction", "document_name",
                "section_count", "block_count", "table_count", "nested_table_count", "row_count",
                "cell_count", "text_character_count", "related_link_count", "elapsed_millis",
                "status", "error_message").get();
        try {
            Files.createDirectories(target.getParent());
            try (var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                try (CSVPrinter csv = new CSVPrinter(writer, format)) {
                    for (var row : result.rows()) {
                        var m = row.metrics();
                        csv.printRecord(row.documentId(), row.receiptNo(), row.fileName(), row.correction(),
                                empty(row.documentName()), m == null ? "" : m.sectionCount(),
                                m == null ? "" : m.totalBlockCount(), m == null ? "" : m.tableCount(),
                                m == null ? "" : m.nestedTableCount(), m == null ? "" : m.tableRowCount(),
                                m == null ? "" : m.tableCellCount(), m == null ? "" : m.textCharacterCount(),
                                row.relatedLinkCount(), row.elapsedMillis(), row.status(), empty(row.errorMessage()));
                    }
                }
            }
            return target;
        } catch (IOException exception) {
            throw new UncheckedIOException("HTML 파싱 검증 보고서 저장 실패", exception);
        }
    }
    private String empty(String value) { return value == null ? "" : value; }
}
