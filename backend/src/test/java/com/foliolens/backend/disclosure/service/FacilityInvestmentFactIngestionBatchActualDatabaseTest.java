package com.foliolens.backend.disclosure.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 최종 스냅샷의 신규시설투자등 원문 전체(43건)를 대상으로 시설투자
 * Fact 일괄 적재를 실제로 실행하는 선택형 감사 테스트.
 *
 * 일반 test 실행에서는 건너뛰고 FOLIOLENS_ACTUAL_DB_AUDIT=true일 때만
 * 실행한다. 개별 접수번호의 추출·검증 실패는 이 테스트를 실패시키지
 * 않는다 — 실제 43건 코퍼스가 골든 케이스와 다른 표 구조·레이블을 얼마나
 * 갖는지 보고하는 것이 목적이며, 결과는 로그와 어서션 실패 메시지로
 * 남긴다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "FOLIOLENS_ACTUAL_DB_AUDIT",
        matches = "true"
)
class FacilityInvestmentFactIngestionBatchActualDatabaseTest {

    private static final String RAW_SUBTYPE = "신규시설투자등";

    @Autowired
    private FacilityInvestmentFactIngestionBatchService batchService;

    @Test
    void 전체_신규시설투자등_원문의_적재_결과를_보고한다() {
        FacilityInvestmentFactIngestionBatchResult result =
                batchService.ingestAll(RAW_SUBTYPE);

        StringBuilder report = new StringBuilder();
        report.append("total=").append(result.totalCount())
                .append(", success=").append(result.successes().size())
                .append(", coreComplete=").append(result.coreCompleteCount())
                .append(", failed=").append(result.failures().size())
                .append(System.lineSeparator());

        result.successes().forEach(success -> report
                .append("  OK   receiptNo=").append(success.receiptNo())
                .append(" allCoreFacts=").append(success.hasAllCoreFacts())
                .append(" missing=").append(success.missingCoreDefinitions())
                .append(" skipped=").append(success.skippedDefinitions())
                .append(" warnings=").append(success.extractionWarnings())
                .append(System.lineSeparator()));
        result.failures().forEach((receiptNo, reason) -> report
                .append("  FAIL receiptNo=").append(receiptNo)
                .append(" reason=").append(reason)
                .append(System.lineSeparator()));

        System.out.println(report);

        assertThat(result.totalCount())
                .as("적재 대상 접수번호 수")
                .isEqualTo(43);
    }
}
