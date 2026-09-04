package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 원문 유형 하나에 속한 모든 접수번호의 시설투자 Fact를 순회 적재한다.
 *
 * 한 접수번호의 추출·검증·저장 실패는 {@link FacilityInvestmentFactIngestionService}가
 * 예외로 알리므로, 여기서는 접수번호 단위로 잡아 나머지 접수번호 처리를
 * 막지 않는다. 각 접수번호는 {@code ingestByReceiptNo} 자체 트랜잭션 안에서
 * 처리되므로 한 건의 실패가 다른 건의 저장을 롤백하지 않는다.
 */
@Service
public class FacilityInvestmentFactIngestionBatchService {

    private final DisclosureDocumentRepository documentRepository;
    private final FacilityInvestmentFactIngestionService ingestionService;

    public FacilityInvestmentFactIngestionBatchService(
            DisclosureDocumentRepository documentRepository,
            FacilityInvestmentFactIngestionService ingestionService
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.ingestionService = Objects.requireNonNull(
                ingestionService,
                "ingestionService는 필수입니다."
        );
    }

    public FacilityInvestmentFactIngestionBatchResult ingestAll(
            String rawSubtype
    ) {
        if (rawSubtype == null || rawSubtype.isBlank()) {
            throw new IllegalArgumentException("rawSubtype은 필수입니다.");
        }

        List<String> receiptNos = documentRepository
                .findFacilityFactIngestionReceiptNos(rawSubtype.strip());

        List<FacilityInvestmentFactIngestionResult> successes =
                new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();

        for (String receiptNo : receiptNos) {
            try {
                successes.add(ingestionService.ingestByReceiptNo(receiptNo));
            } catch (RuntimeException exception) {
                failures.put(receiptNo, describe(exception));
            }
        }

        return new FacilityInvestmentFactIngestionBatchResult(
                successes,
                failures
        );
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
