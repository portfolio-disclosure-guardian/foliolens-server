package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HtmlParsingValidationReportWriterTest {
    @Test void writesBomAndPreservesEmptyColumnsAndQuotedErrors(@TempDir Path directory) throws Exception {
        var row = new HtmlParsingValidationRow(UUID.randomUUID(), "20240424800596", "file.xml",
                true, null, null, 0, 15, HtmlParsingValidationRow.Status.FAILED, "오류, \"표\"\n확인");
        var result = new HtmlParsingValidationBatchResult(Instant.now(), Instant.now(), 1, List.of(row));
        Path file = new HtmlParsingValidationReportWriter().write(directory.resolve("result.csv"), result);
        String text = Files.readString(file);
        assertThat(text.charAt(0)).isEqualTo('\uFEFF');
        try (var csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new java.io.StringReader(text.substring(1)))) {
            var records = csv.getRecords();
            assertThat(records.getFirst().size()).isEqualTo(16);
            assertThat(records.getFirst().get("document_name")).isEmpty();
            assertThat(records.getFirst().get("status")).isEqualTo("FAILED");
            assertThat(records.getFirst().get("elapsed_millis")).isEqualTo("15");
            assertThat(records.getFirst().get("error_message")).isEqualTo(row.errorMessage());
        }
    }
}
