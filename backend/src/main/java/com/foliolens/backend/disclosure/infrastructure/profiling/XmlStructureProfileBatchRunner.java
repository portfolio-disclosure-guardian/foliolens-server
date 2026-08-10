package com.foliolens.backend.disclosure.infrastructure.profiling;

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
 * 애플리케이션 시작 시 소규모 XML 구조 배치 조사를 실행하고 CSV로 저장한다.
 */
@Slf4j
@Component
@Order(5)
@ConditionalOnProperty(
        prefix = "foliolens.profiling.xml-structure.batch",
        name = "enabled",
        havingValue = "true"
)
public class XmlStructureProfileBatchRunner implements ApplicationRunner {

    private final XmlStructureProfileBatchService batchService;
    private final XmlStructureProfileReportWriter reportWriter;
    private final int page;
    private final int limit;
    private final Path reportPath;

    public XmlStructureProfileBatchRunner(
            XmlStructureProfileBatchService batchService,
            XmlStructureProfileReportWriter reportWriter,

            @Value("${foliolens.profiling.xml-structure.batch.page:0}")
            int configuredPage,

            @Value("${foliolens.profiling.xml-structure.batch.limit:50}")
            int configuredLimit,

            @Value("${foliolens.profiling.xml-structure.batch.report-path:./reports/xml-structure-profile.csv}")
            String configuredReportPath
    ) {
        this.batchService = batchService;
        this.reportWriter = reportWriter;
        this.page = configuredPage;
        this.limit = configuredLimit;
        this.reportPath = parseReportPath(configuredReportPath);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "XML 구조 배치 조사를 시작합니다. page={}, limit={}, reportPath={}",
                page,
                limit,
                reportPath
        );

        XmlStructureProfileBatchResult result = batchService.profile(page, limit);

        long startSequence = (long) page * limit + 1;
        long endSequence = (long) page * limit + result.totalCount();

        Path savedReportPath = reportWriter.write(reportPath, result);

        if (result.hasFailures()) {
            log.warn(
                    "일부 XML 구조 조사에 실패했습니다. "
                            + "page={}, startSequence={}, endSequence={}, "
                            + "totalCount={}, successCount={}, "
                            + "failedCount={}, elapsedMillis={}, "
                            + "reportPath={}",
                    page,
                    startSequence,
                    endSequence,
                    result.totalCount(),
                    result.successCount(),
                    result.failedCount(),
                    result.elapsedMillis(),
                    savedReportPath
            );

            result.rows().stream()
                    .filter(row ->
                            row.status()
                                    == XmlStructureProfileStatus.FAILED
                    )
                    .limit(5)
                    .forEach(row ->
                            log.warn(
                                    "XML 구조 조사 실패: receiptNo={}, "
                                            + "fileName={}, reason={}",
                                    row.receiptNo(),
                                    row.fileName(),
                                    row.errorMessage()
                            )
                    );

            return;
        }

        log.info(
                "XML 구조 배치 조사가 완료되었습니다. "
                        + "page={}, startSequence={}, endSequence={}, "
                        + "totalCount={}, successCount={}, "
                        + "elapsedMillis={}, reportPath={}",
                page,
                startSequence,
                endSequence,
                result.totalCount(),
                result.successCount(),
                result.elapsedMillis(),
                savedReportPath
        );
    }

    private static Path parseReportPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "XML 구조 조사 결과 CSV 경로는 필수입니다."
            );
        }

        String normalized = value.trim();

        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "XML 구조 조사 결과 경로는 .csv 파일이어야 합니다. path="
                            + normalized
            );
        }

        try {
            return Path.of(normalized).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "XML 구조 조사 결과 CSV 경로가 올바르지 않습니다. path="
                            + normalized,
                    exception
            );
        }
    }
}
