package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

@Component
public class XmlParsingValidationReportWriter {

    private static final char UTF_8_BOM = '\uFEFF';

    private static final CSVFormat CSV_FORMAT =
            CSVFormat.DEFAULT.builder()
                    .setHeader(
                            "disclosure_document_id",
                            "receipt_no",
                            "source_group",
                            "report_name",
                            "file_name",
                            "document_role",
                            "file_size_bytes",

                            "parsed_document_name",
                            "section_count",
                            "max_section_level",
                            "total_block_count",
                            "heading_count",
                            "paragraph_count",
                            "page_break_count",
                            "table_count",
                            "nested_table_count",
                            "table_row_count",
                            "table_cell_count",
                            "image_count",
                            "text_character_count",

                            "elapsed_millis",
                            "status",
                            "warning_message",
                            "error_type",
                            "error_line",
                            "error_column",
                            "error_message"
                    )
                    .get();

    public Path write(
            Path outputPath,
            XmlParsingValidationBatchResult batchResult
    ) {
        Path reportPath = normalizeOutputPath(outputPath);
        XmlParsingValidationBatchResult result =
                Objects.requireNonNull(
                        batchResult,
                        "batchResult는 필수입니다."
                );

        prepareOutputDirectory(reportPath);

        try (
                BufferedWriter writer = Files.newBufferedWriter(
                        reportPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                )
        ) {
            // Windows Excel에서도 UTF-8 한글을 올바르게 인식하도록 BOM을 기록한다.
            writer.write(UTF_8_BOM);

            try (CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
                for (XmlParsingValidationRow row : result.rows()) {
                    printRow(printer, row);
                }
            }

            return reportPath;
        } catch (IOException exception) {
            throw datasetException(
                    "XML 파싱 검증 CSV를 저장하지 못했습니다. path="
                            + reportPath,
                    exception
            );
        }
    }

    private void printRow(
            CSVPrinter printer,
            XmlParsingValidationRow row
    ) throws IOException {
        printer.printRecord(
                row.disclosureDocumentId(),
                row.receiptNo(),
                row.sourceGroup(),
                row.reportName(),
                row.fileName(),
                row.documentRole(),
                row.fileSizeBytes(),

                emptyIfNull(row.parsedDocumentName()),
                row.sectionCount(),
                row.maxSectionLevel(),
                row.totalBlockCount(),
                row.headingCount(),
                row.paragraphCount(),
                row.pageBreakCount(),
                row.tableCount(),
                row.nestedTableCount(),
                row.tableRowCount(),
                row.tableCellCount(),
                row.imageCount(),
                row.textCharacterCount(),

                row.elapsedMillis(),
                row.status().name(),
                emptyIfNull(row.warningMessage()),
                emptyIfNull(row.errorType()),
                emptyIfNull(row.errorLine()),
                emptyIfNull(row.errorColumn()),
                emptyIfNull(row.errorMessage())
        );
    }

    private Path normalizeOutputPath(Path outputPath) {
        if (outputPath == null) {
            throw datasetException(
                    "XML 파싱 검증 결과 경로가 설정되지 않았습니다."
            );
        }

        Path normalized = outputPath
                .toAbsolutePath()
                .normalize();

        if (Files.exists(normalized) && Files.isDirectory(normalized)) {
            throw datasetException(
                    "XML 파싱 검증 결과 경로는 디렉터리가 아닌 "
                            + "파일이어야 합니다. path="
                            + normalized
            );
        }

        return normalized;
    }

    private void prepareOutputDirectory(Path reportPath) {
        Path parent = reportPath.getParent();

        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw datasetException(
                    "XML 파싱 검증 CSV 디렉터리를 생성하지 못했습니다. path="
                            + parent,
                    exception
            );
        }
    }

    private String emptyIfNull(Object value) {
        return value == null ? "" : value.toString();
    }

    private BusinessException datasetException(String message) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }

    private BusinessException datasetException(
            String message,
            Throwable cause
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message,
                cause
        );
    }
}
