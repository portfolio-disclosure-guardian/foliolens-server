package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@Order(7)
@ConditionalOnProperty(prefix = "foliolens.validation.html-parsing.batch", name = "enabled", havingValue = "true")
public class HtmlParsingValidationBatchRunner implements ApplicationRunner {
    private final HtmlParsingValidationBatchService service;
    private final HtmlParsingValidationReportWriter writer;
    private final int page;
    private final int limit;
    private final int expectedCount;
    private final String subtype;
    private final Path report;

    public HtmlParsingValidationBatchRunner(HtmlParsingValidationBatchService service,
            HtmlParsingValidationReportWriter writer,
            @Value("${foliolens.validation.html-parsing.batch.page:0}") int page,
            @Value("${foliolens.validation.html-parsing.batch.limit:50}") int limit,
            @Value("${foliolens.validation.html-parsing.batch.expected-count:43}") int expectedCount,
            @Value("${foliolens.validation.html-parsing.batch.raw-subtype:신규시설투자등}") String subtype,
            @Value("${foliolens.validation.html-parsing.batch.report-path:./reports/html-parsing-validation.csv}") String report) {
        this.service = service;
        this.writer = writer;
        this.page = page;
        this.limit = limit;
        this.expectedCount = expectedCount;
        this.subtype = subtype;
        this.report = Path.of(report);
    }

    @Override public void run(ApplicationArguments args) {
        var result = service.validate(page, limit, subtype);
        Path saved = writer.write(report, result);
        log.info("HTML 파싱 검증: 대상={}, 검사={}, 성공={}, 실패={}, elapsedMillis={}, report={}",
                result.totalTargetCount(), result.rows().size(), result.successCount(),
                result.failedCount(), result.elapsedMillis(), saved);
        if (result.failedCount() > 0) {
            result.rows().stream().filter(r -> r.status() == HtmlParsingValidationRow.Status.FAILED)
                    .limit(5).forEach(r -> log.warn("HTML 파싱 실패: receiptNo={}, reason={}", r.receiptNo(), r.errorMessage()));
            throw new IllegalStateException("HTML 검증 실패가 있습니다. CSV를 확인한 뒤 적재하세요.");
        }
        if (expectedCount > 0 && (result.totalTargetCount() != expectedCount
                || result.rows().size() != expectedCount || page != 0)) {
            throw new IllegalStateException("예상 " + expectedCount + "건의 전체 검증이 아닙니다. 대상/limit을 확인하세요.");
        }
    }
}
