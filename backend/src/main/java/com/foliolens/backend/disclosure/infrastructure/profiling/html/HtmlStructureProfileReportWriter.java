package com.foliolens.backend.disclosure.infrastructure.profiling.html;

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

/**
 * HTML 구조 배치 조사 결과를 Excel 호환 UTF-8 CSV로 저장한다.
 */
@Component
public class HtmlStructureProfileReportWriter {

    private static final char UTF_8_BOM = '\uFEFF';

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(
                    "disclosure_document_id",
                    "source_doc_id",
                    "receipt_no",
                    "source_group",
                    "raw_subtype",
                    "report_name",
                    "correction",
                    "file_name",
                    "document_role",
                    "content_format",
                    "file_size_bytes",
                    "relative_path",
                    "root_element_name",
                    "document_name",
                    "html_title",
                    "decoded_charset",
                    "declared_charset",
                    "max_depth",
                    "distinct_tag_count",
                    "total_element_count",
                    "tag_counts_summary",
                    "class_counts_summary",
                    "xforms_container_count",
                    "xforms_title_count",
                    "xforms_input_count",
                    "table_count",
                    "top_level_table_count",
                    "nested_table_count",
                    "max_table_depth",
                    "table_row_count",
                    "table_header_count",
                    "table_cell_count",
                    "max_rows_per_table",
                    "max_cells_per_row",
                    "row_span_cell_count",
                    "max_row_span",
                    "col_span_cell_count",
                    "max_col_span",
                    "invalid_span_attribute_count",
                    "line_break_count",
                    "anchor_count",
                    "anchor_with_href_count",
                    "image_count",
                    "style_count",
                    "script_count",
                    "comment_count",
                    "parser_error_count",
                    "first_parser_error",
                    "elapsed_millis",
                    "status",
                    "error_type",
                    "error_message"
            )
            .get();

    public Path write(
            Path outputPath,
            HtmlStructureProfileBatchResult batchResult
    ) {
        Path reportPath = normalizeOutputPath(outputPath);
        HtmlStructureProfileBatchResult result = Objects.requireNonNull(
                batchResult,
                "batchResult는 필수입니다."
        );
        prepareOutputDirectory(reportPath);

        try (BufferedWriter writer = Files.newBufferedWriter(
                reportPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            writer.write(UTF_8_BOM);
            try (CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
                for (HtmlStructureProfileRow row : result.rows()) {
                    printRow(printer, row);
                }
            }
            return reportPath;
        } catch (IOException exception) {
            throw datasetException(
                    "HTML 구조 조사 결과 CSV를 저장할 수 없습니다. path="
                            + reportPath,
                    exception
            );
        }
    }

    private void printRow(
            CSVPrinter printer,
            HtmlStructureProfileRow row
    ) throws IOException {
        printer.printRecord(
                row.disclosureDocumentId(),
                row.sourceDocId(),
                row.receiptNo(),
                row.sourceGroup(),
                emptyIfNull(row.rawSubtype()),
                row.reportName(),
                row.correction(),
                row.fileName(),
                row.documentRole(),
                row.contentFormat(),
                row.fileSizeBytes(),
                row.relativePath(),
                emptyIfNull(row.rootElementName()),
                emptyIfNull(row.documentName()),
                emptyIfNull(row.htmlTitle()),
                emptyIfNull(row.decodedCharset()),
                emptyIfNull(row.declaredCharset()),
                row.maxDepth(),
                row.distinctTagCount(),
                row.totalElementCount(),
                emptyIfNull(row.tagCountsSummary()),
                emptyIfNull(row.classCountsSummary()),
                row.xformsContainerCount(),
                row.xformsTitleCount(),
                row.xformsInputCount(),
                row.tableCount(),
                row.topLevelTableCount(),
                row.nestedTableCount(),
                row.maxTableDepth(),
                row.tableRowCount(),
                row.tableHeaderCount(),
                row.tableCellCount(),
                row.maxRowsPerTable(),
                row.maxCellsPerRow(),
                row.rowSpanCellCount(),
                row.maxRowSpan(),
                row.colSpanCellCount(),
                row.maxColSpan(),
                row.invalidSpanAttributeCount(),
                row.lineBreakCount(),
                row.anchorCount(),
                row.anchorWithHrefCount(),
                row.imageCount(),
                row.styleCount(),
                row.scriptCount(),
                row.commentCount(),
                row.parserErrorCount(),
                emptyIfNull(row.firstParserError()),
                row.elapsedMillis(),
                row.status().name(),
                emptyIfNull(row.errorType()),
                emptyIfNull(row.errorMessage())
        );
    }

    private Path normalizeOutputPath(Path outputPath) {
        if (outputPath == null) {
            throw datasetException("HTML 구조 조사 결과 경로는 필수입니다.");
        }
        Path normalized = outputPath.toAbsolutePath().normalize();
        if (Files.exists(normalized) && Files.isDirectory(normalized)) {
            throw datasetException(
                    "HTML 구조 조사 결과 경로는 파일이어야 합니다. path="
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
                    "HTML 구조 조사 결과 디렉터리를 생성할 수 없습니다. path="
                            + parent,
                    exception
            );
        }
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private BusinessException datasetException(String message) {
        return new BusinessException(ErrorCode.DATASET_503_1, message);
    }

    private BusinessException datasetException(
            String message,
            Throwable cause
    ) {
        return new BusinessException(ErrorCode.DATASET_503_1, message, cause);
    }
}
