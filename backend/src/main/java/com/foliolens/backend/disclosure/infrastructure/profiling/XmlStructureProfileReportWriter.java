package com.foliolens.backend.disclosure.infrastructure.profiling;

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
 * XML 구조 배치 조사 결과를 CSV 파일로 저장한다.
 */
@Component
public class XmlStructureProfileReportWriter {

    private static final char UTF_8_BOM = '\uFEFF';

    private static final CSVFormat CSV_FORMAT =
            CSVFormat.DEFAULT.builder()
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
                            "max_depth",
                            "distinct_tag_count",
                            "total_element_count",
                            "section_1_count",
                            "section_2_count",
                            "section_3_count",
                            "title_count",
                            "paragraph_count",
                            "table_count",
                            "table_row_count",
                            "table_header_count",
                            "table_cell_count",
                            "repaired_ampersand_count",
                            "repaired_less_than_count",
                            "elapsed_millis",
                            "status",
                            "error_type",
                            "error_line",
                            "error_column",
                            "error_message"
                    )
                    .get();

    /**
     * 배치 결과를 지정한 CSV 파일에 저장하고 실제 저장 경로를 반환한다.
     *
     * 부모 디렉터리가 없으면 생성하며, 같은 경로의 기존 파일은 덮어쓴다.
     */
    public Path write(Path outputPath, XmlStructureProfileBatchResult batchResult) {
        Path reportPath = normalizeOutputPath(outputPath);
        XmlStructureProfileBatchResult result =
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
            // Windows Excel에서도 한글을 UTF-8로 인식하도록 BOM을 기록한다.
            writer.write(UTF_8_BOM);

            try (CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
                for (XmlStructureProfileRow row : result.rows()) {
                    printRow(printer, row);
                }
            }

            return reportPath;
        } catch (IOException exception) {
            throw datasetException(
                    "XML 구조 조사 결과 CSV를 저장할 수 없습니다. path="
                            + reportPath,
                    exception
            );
        }
    }

    private void printRow(
            CSVPrinter printer,
            XmlStructureProfileRow row
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
                row.maxDepth(),
                row.distinctTagCount(),
                row.totalElementCount(),
                row.section1Count(),
                row.section2Count(),
                row.section3Count(),
                row.titleCount(),
                row.paragraphCount(),
                row.tableCount(),
                row.tableRowCount(),
                row.tableHeaderCount(),
                row.tableCellCount(),
                row.repairedAmpersandCount(),
                row.repairedLessThanCount(),
                row.elapsedMillis(),
                row.status().name(),
                emptyIfNull(row.errorType()),
                row.errorLine(),
                row.errorColumn(),
                emptyIfNull(row.errorMessage())
        );
    }

    private Path normalizeOutputPath(Path outputPath) {
        if (outputPath == null) {
            throw datasetException(
                    "XML 구조 조사 결과 CSV 경로가 설정되지 않았습니다."
            );
        }

        Path normalized = outputPath.toAbsolutePath().normalize();

        if (Files.exists(normalized) && Files.isDirectory(normalized)) {
            throw datasetException(
                    "XML 구조 조사 결과 경로는 디렉터리가 아닌 파일이어야 합니다. path="
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
                    "XML 구조 조사 결과 디렉터리를 생성할 수 없습니다. path="
                            + parent,
                    exception
            );
        }
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
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
