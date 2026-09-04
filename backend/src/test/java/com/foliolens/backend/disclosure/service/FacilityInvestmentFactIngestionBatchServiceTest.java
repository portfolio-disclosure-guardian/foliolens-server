package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityInvestmentFactIngestionBatchServiceTest {

    private static final String RAW_SUBTYPE = "신규시설투자등";
    private static final String RECEIPT_1 = "20240424800596";
    private static final String RECEIPT_2 = "20230523900365";
    private static final String RECEIPT_3 = "20240813800252";

    private DisclosureDocumentRepository documentRepository;
    private FacilityInvestmentFactIngestionService ingestionService;
    private FacilityInvestmentFactIngestionBatchService batchService;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        ingestionService = mock(FacilityInvestmentFactIngestionService.class);
        batchService = new FacilityInvestmentFactIngestionBatchService(
                documentRepository,
                ingestionService
        );
    }

    @Test
    void 한_접수번호의_적재_실패가_나머지_접수번호_처리를_막지_않는다() {
        when(documentRepository
                .findFacilityFactIngestionReceiptNos(RAW_SUBTYPE))
                .thenReturn(List.of(RECEIPT_1, RECEIPT_2, RECEIPT_3));
        when(ingestionService.ingestByReceiptNo(RECEIPT_1))
                .thenReturn(result(RECEIPT_1, Set.of()));
        when(ingestionService.ingestByReceiptNo(RECEIPT_2))
                .thenThrow(new IllegalStateException(
                        "검증된 시설투자 Fact가 없어 기존 적재값을 교체하지 않습니다."
                ));
        when(ingestionService.ingestByReceiptNo(RECEIPT_3))
                .thenReturn(result(RECEIPT_3, Set.of()));

        FacilityInvestmentFactIngestionBatchResult result =
                batchService.ingestAll(RAW_SUBTYPE);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.successes()).hasSize(2);
        assertThat(result.successes())
                .extracting(FacilityInvestmentFactIngestionResult::receiptNo)
                .containsExactly(RECEIPT_1, RECEIPT_3);
        assertThat(result.failures())
                .containsOnlyKeys(RECEIPT_2)
                .hasEntrySatisfying(RECEIPT_2, reason -> assertThat(reason)
                        .contains("IllegalStateException")
                        .contains("기존 적재값을 교체하지 않습니다"));

        verify(ingestionService).ingestByReceiptNo(RECEIPT_1);
        verify(ingestionService).ingestByReceiptNo(RECEIPT_2);
        verify(ingestionService).ingestByReceiptNo(RECEIPT_3);
    }

    @Test
    void 대상_접수번호가_없으면_빈_결과를_반환한다() {
        when(documentRepository
                .findFacilityFactIngestionReceiptNos(RAW_SUBTYPE))
                .thenReturn(List.of());

        FacilityInvestmentFactIngestionBatchResult result =
                batchService.ingestAll(RAW_SUBTYPE);

        assertThat(result.totalCount()).isZero();
        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void rawSubtype이_비어있으면_예외를_던진다() {
        assertThatThrownBy(() -> batchService.ingestAll(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batchService.ingestAll(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coreCompleteCount은_핵심_Fact를_모두_갖춘_성공만_센다() {
        when(documentRepository
                .findFacilityFactIngestionReceiptNos(RAW_SUBTYPE))
                .thenReturn(List.of(RECEIPT_1, RECEIPT_2));
        when(ingestionService.ingestByReceiptNo(RECEIPT_1))
                .thenReturn(result(RECEIPT_1, Set.of()));
        when(ingestionService.ingestByReceiptNo(RECEIPT_2))
                .thenReturn(result(
                        RECEIPT_2,
                        Set.of(FacilityInvestmentFactDefinition.TARGET)
                ));

        FacilityInvestmentFactIngestionBatchResult result =
                batchService.ingestAll(RAW_SUBTYPE);

        assertThat(result.successes()).hasSize(2);
        assertThat(result.coreCompleteCount()).isEqualTo(1);
    }

    private FacilityInvestmentFactIngestionResult result(
            String receiptNo,
            Set<FacilityInvestmentFactDefinition> missingCoreDefinitions
    ) {
        return new FacilityInvestmentFactIngestionResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                receiptNo,
                9,
                9 - missingCoreDefinitions.size(),
                9 - missingCoreDefinitions.size(),
                missingCoreDefinitions,
                Map.of(),
                List.of(),
                new DisclosureFactPersistenceResult(0, 0, 1, 1, 1)
        );
    }
}
