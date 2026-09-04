package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.FacilityInvestmentFactIngestionResult;
import com.foliolens.backend.disclosure.service.FacilityInvestmentFactIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 설정으로 명시한 접수번호 한 건, 또는 receipt-no가 비어 있으면 파싱·청킹이
 * 끝난 모든 신규시설투자등 원문의 시설투자 Fact를 시작 시 적재한다.
 */
@Slf4j
@Component
@Order(20)
@ConditionalOnProperty(
        prefix = "foliolens.fact.facility-ingestion",
        name = "enabled",
        havingValue = "true"
)
public class FacilityInvestmentFactIngestionRunner implements ApplicationRunner {

    private static final String RAW_SUBTYPE = "신규시설투자등";

    private final FacilityInvestmentFactIngestionService ingestionService;
    private final DisclosureDocumentRepository documentRepository;
    private final String receiptNo;

    public FacilityInvestmentFactIngestionRunner(
            FacilityInvestmentFactIngestionService ingestionService,
            DisclosureDocumentRepository documentRepository,
            @Value("${foliolens.fact.facility-ingestion.receipt-no:}")
            String receiptNo
    ) {
        this.ingestionService = Objects.requireNonNull(
                ingestionService,
                "ingestionService는 필수입니다."
        );
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.receiptNo = receiptNo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (receiptNo == null || receiptNo.isBlank()) {
            runBatch();
            return;
        }
        ingestAndLog(receiptNo);
    }

    private void runBatch() {
        List<String> receiptNos = documentRepository
                .findAllByContentFormatAndDisclosure_RawSubtypeOrderByIdAsc(
                        DisclosureDocumentContentFormat.HTML,
                        RAW_SUBTYPE,
                        PageRequest.of(0, 200)
                )
                .stream()
                .map(document -> document.getDisclosure().getReceiptNo())
                .distinct()
                .toList();

        log.info("시설투자 Fact 배치 적재를 시작합니다. targetCount={}", receiptNos.size());

        int successCount = 0;
        int failureCount = 0;
        for (String targetReceiptNo : receiptNos) {
            try {
                ingestAndLog(targetReceiptNo);
                successCount++;
            } catch (RuntimeException exception) {
                failureCount++;
                log.warn(
                        "시설투자 Fact 적재를 건너뜁니다. receiptNo={}, reason={}",
                        targetReceiptNo,
                        exception.getMessage()
                );
            }
        }

        log.info(
                "시설투자 Fact 배치 적재가 끝났습니다. total={}, success={}, failure={}",
                receiptNos.size(),
                successCount,
                failureCount
        );
    }

    private void ingestAndLog(String targetReceiptNo) {
        log.info("시설투자 Fact 적재를 시작합니다. receiptNo={}", targetReceiptNo);

        FacilityInvestmentFactIngestionResult result =
                ingestionService.ingestByReceiptNo(targetReceiptNo);
        DisclosureFactPersistenceResult stored = result.persistenceResult();

        log.info(
                "시설투자 Fact 적재가 완료됐습니다. receiptNo={}, documentId={}, "
                        + "candidateEvidenceCount={}, verifiedEvidenceCount={}, "
                        + "generatedFactCount={}, savedFactCount={}, "
                        + "savedEvidenceCount={}, linkCount={}, allCoreFacts={}",
                result.receiptNo(),
                result.disclosureDocumentId(),
                result.candidateEvidenceCount(),
                result.verifiedEvidenceCount(),
                result.generatedFactCount(),
                stored.savedFactCount(),
                stored.savedEvidenceCount(),
                stored.savedLinkCount(),
                result.hasAllCoreFacts()
        );

        if (!result.skippedDefinitions().isEmpty()) {
            log.warn(
                    "승격하지 못한 시설투자 Fact가 있습니다. receiptNo={}, skipped={}",
                    result.receiptNo(),
                    result.skippedDefinitions()
            );
        }
        if (!result.extractionWarnings().isEmpty()) {
            log.warn(
                    "시설투자 Evidence 추출 경고가 있습니다. receiptNo={}, warnings={}",
                    result.receiptNo(),
                    result.extractionWarnings()
            );
        }
    }
}
