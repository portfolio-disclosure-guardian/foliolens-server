package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.service.FacilityInvestmentFactIngestionBatchResult;
import com.foliolens.backend.disclosure.service.FacilityInvestmentFactIngestionBatchService;
import com.foliolens.backend.disclosure.service.FacilityInvestmentFactIngestionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 원문 유형 하나에 속한 모든 접수번호의 시설투자 Fact를 시작 시 일괄 적재한다. */
@Slf4j
@Component
@Order(21)
@ConditionalOnProperty(
        prefix = "foliolens.fact.facility-ingestion-batch",
        name = "enabled",
        havingValue = "true"
)
public class FacilityInvestmentFactIngestionBatchRunner
        implements ApplicationRunner {

    private final FacilityInvestmentFactIngestionBatchService batchService;
    private final String rawSubtype;

    public FacilityInvestmentFactIngestionBatchRunner(
            FacilityInvestmentFactIngestionBatchService batchService,
            @Value("${foliolens.fact.facility-ingestion-batch.raw-subtype:신규시설투자등}")
            String rawSubtype
    ) {
        this.batchService = batchService;
        this.rawSubtype = rawSubtype;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("시설투자 Fact 일괄 적재를 시작합니다. rawSubtype={}", rawSubtype);

        FacilityInvestmentFactIngestionBatchResult result =
                batchService.ingestAll(rawSubtype);

        for (FacilityInvestmentFactIngestionResult success
                : result.successes()) {
            log.info(
                    "시설투자 Fact 적재 성공. receiptNo={}, "
                            + "candidateEvidenceCount={}, verifiedEvidenceCount={}, "
                            + "generatedFactCount={}, allCoreFacts={}, "
                            + "missingCoreDefinitions={}, skippedDefinitions={}",
                    success.receiptNo(),
                    success.candidateEvidenceCount(),
                    success.verifiedEvidenceCount(),
                    success.generatedFactCount(),
                    success.hasAllCoreFacts(),
                    success.missingCoreDefinitions(),
                    success.skippedDefinitions()
            );
        }
        result.failures().forEach((receiptNo, reason) -> log.warn(
                "시설투자 Fact 적재 실패. receiptNo={}, reason={}",
                receiptNo,
                reason
        ));

        log.info(
                "시설투자 Fact 일괄 적재를 마쳤습니다. total={}, success={}, "
                        + "coreComplete={}, failed={}",
                result.totalCount(),
                result.successes().size(),
                result.coreCompleteCount(),
                result.failures().size()
        );
    }
}
