package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 애플리케이션 시작 시 제한된 수의 DART XML을 실제 파싱하고,
 * 검증 결과를 CSV 파일로 저장한다.
 *
 * 이 단계에서는 DB의 parse_status를 변경하거나
 * 파싱 결과 엔티티를 저장하지 않는다.
 */
@Slf4j
@Component
@Order(6)
@ConditionalOnProperty(
        prefix = "foliolens.validation.xml-parsing.batch",
        name = "enabled",
        havingValue = "true"
)
public class XmlParsingValidationBatchRunner
        implements ApplicationRunner {

    private final XmlParsingValidationBatchService batchService;
    private final XmlParsingValidationReportWriter reportWriter;

    private final int page;
    private final int limit;
    private final Path reportPath;

    public XmlParsingValidationBatchRunner(
            XmlParsingValidationBatchService batchService,
            XmlParsingValidationReportWriter reportWriter,

            @Value(
                    "${foliolens.validation.xml-parsing.batch.page:0}"
            )
            int configuredPage,

            @Value(
                    "${foliolens.validation.xml-parsing.batch.limit:50}"
            )
            int configuredLimit,

            @Value(
                    "${foliolens.validation.xml-parsing.batch.report-path:"
                            + "./reports/xml-parsing-validation.csv}"
            )
            String configuredReportPath
    ) {
        this.batchService = batchService;
        this.reportWriter = reportWriter;
        this.page = configuredPage;
        this.limit = configuredLimit;
        this.reportPath =
                parseReportPath(configuredReportPath);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "XML 파싱 배치 검증을 시작합니다. "
                        + "page={}, limit={}, reportPath={}",
                page,
                limit,
                reportPath
        );

        XmlParsingValidationBatchResult result =
                batchService.validate(page, limit);

        Path savedReportPath =
                reportWriter.write(reportPath, result);

        long startSequence =
                (long) page * limit + 1;

        long endSequence =
                (long) page * limit
                        + result.totalCount();

        logValidationIssues(result);

        if (result.hasFailures()) {
            log.warn(
                    "XML 파싱 배치 검증이 일부 실패로 완료됐습니다. "
                            + "page={}, startSequence={}, "
                            + "endSequence={}, totalCount={}, "
                            + "successCount={}, warningCount={}, "
                            + "failedCount={}, elapsedMillis={}, "
                            + "reportPath={}",
                    page,
                    startSequence,
                    endSequence,
                    result.totalCount(),
                    result.successCount(),
                    result.warningCount(),
                    result.failedCount(),
                    result.elapsedMillis(),
                    savedReportPath
            );

            return;
        }

        if (result.warningCount() > 0) {
            log.warn(
                    "XML 파싱 배치 검증이 경고와 함께 완료됐습니다. "
                            + "page={}, startSequence={}, "
                            + "endSequence={}, totalCount={}, "
                            + "successCount={}, warningCount={}, "
                            + "elapsedMillis={}, reportPath={}",
                    page,
                    startSequence,
                    endSequence,
                    result.totalCount(),
                    result.successCount(),
                    result.warningCount(),
                    result.elapsedMillis(),
                    savedReportPath
            );

            return;
        }

        log.info(
                "XML 파싱 배치 검증이 완료됐습니다. "
                        + "page={}, startSequence={}, "
                        + "endSequence={}, totalCount={}, "
                        + "successCount={}, elapsedMillis={}, "
                        + "reportPath={}",
                page,
                startSequence,
                endSequence,
                result.totalCount(),
                result.successCount(),
                result.elapsedMillis(),
                savedReportPath
        );
    }

    /**
     * WARNING 및 FAILED 결과 중 최대 10개를 로그에 출력한다.
     *
     * 전체 결과는 CSV에 저장하므로 로그를 지나치게 많이 남기지 않는다.
     */
    private void logValidationIssues(
            XmlParsingValidationBatchResult result
    ) {
        result.rows().stream()
                .filter(row ->
                        row.status()
                                != XmlParsingValidationStatus.SUCCESS
                )
                .limit(10)
                .forEach(row -> {
                    if (
                            row.status()
                                    == XmlParsingValidationStatus.WARNING
                    ) {
                        log.warn(
                                "XML 파싱 검증 경고: "
                                        + "receiptNo={}, fileName={}, "
                                        + "warning={}",
                                row.receiptNo(),
                                row.fileName(),
                                row.warningMessage()
                        );

                        return;
                    }

                    log.warn(
                            "XML 파싱 검증 실패: "
                                    + "receiptNo={}, fileName={}, "
                                    + "errorType={}, errorLine={}, "
                                    + "errorColumn={}, errorMessage={}",
                            row.receiptNo(),
                            row.fileName(),
                            row.errorType(),
                            row.errorLine(),
                            row.errorColumn(),
                            row.errorMessage()
                    );
                });
    }

    private static Path parseReportPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "XML 파싱 검증 결과 CSV 경로는 필수입니다."
            );
        }

        String normalized = value.trim();

        if (
                !normalized.toLowerCase(Locale.ROOT)
                        .endsWith(".csv")
        ) {
            throw new IllegalArgumentException(
                    "XML 파싱 검증 결과 경로는 "
                            + ".csv 파일이어야 합니다. path="
                            + normalized
            );
        }

        try {
            return Path.of(normalized)
                    .toAbsolutePath()
                    .normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "XML 파싱 검증 결과 CSV 경로가 "
                            + "올바르지 않습니다. path="
                            + normalized,
                    exception
            );
        }
    }
}
