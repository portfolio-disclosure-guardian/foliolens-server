package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactSet;
import com.foliolens.backend.disclosure.domain.fact.facility.generation.FacilityInvestmentFactGenerator;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerificationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerifier;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.VerifiedFacilityEvidence;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceService;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityInvestmentFactIngestionServiceTest {

    private static final String RECEIPT_NO = "20240424800596";
    private static final UUID DISCLOSURE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private DisclosureDocumentRepository documentRepository;
    private FacilityInvestmentEvidenceExtractionService extractionService;
    private FacilityInvestmentEvidenceVerifier evidenceVerifier;
    private FacilityInvestmentFactGenerator factGenerator;
    private DisclosureFactPersistenceService persistenceService;
    private FacilityInvestmentFactIngestionService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        extractionService = mock(
                FacilityInvestmentEvidenceExtractionService.class
        );
        evidenceVerifier = mock(FacilityInvestmentEvidenceVerifier.class);
        factGenerator = mock(FacilityInvestmentFactGenerator.class);
        persistenceService = mock(DisclosureFactPersistenceService.class);
        service = new FacilityInvestmentFactIngestionService(
                documentRepository,
                extractionService,
                evidenceVerifier,
                factGenerator,
                persistenceService
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void 접수번호의_파싱완료_MAIN_문서를_검증하고_Fact를_교체저장한다() {
        Disclosure disclosure = disclosure();
        DisclosureDocument main = document(
                disclosure,
                DisclosureDocumentRole.MAIN,
                DisclosureDocumentParseStatus.COMPLETED
        );
        DisclosureDocument attachment = document(
                disclosure,
                DisclosureDocumentRole.ATTACHMENT,
                DisclosureDocumentParseStatus.COMPLETED
        );
        DisclosureEvidence candidate = mock(DisclosureEvidence.class);
        DisclosureEvidence verifiedEvidence = mock(DisclosureEvidence.class);
        DisclosureFact fact = mock(DisclosureFact.class);
        VerifiedFacilityEvidence verified = mock(
                VerifiedFacilityEvidence.class
        );
        FacilityInvestmentEvidenceVerificationResult verification = mock(
                FacilityInvestmentEvidenceVerificationResult.class
        );
        FacilityInvestmentFactSet factSet = mock(
                FacilityInvestmentFactSet.class
        );
        FacilityInvestmentEvidenceExtractionResult extraction =
                new FacilityInvestmentEvidenceExtractionResult(
                        Map.of(
                                FacilityInvestmentFactDefinition.AMOUNT,
                                List.of(candidate)
                        ),
                        List.of("테스트 경고")
                );
        DisclosureFactPersistenceResult stored =
                new DisclosureFactPersistenceResult(0, 0, 1, 1, 1);

        when(documentRepository
                .findAllByDisclosure_ReceiptNoOrderByIdAsc(RECEIPT_NO))
                .thenReturn(List.of(attachment, main));
        when(extractionService.extract(DOCUMENT_ID)).thenReturn(extraction);
        when(evidenceVerifier.verify(extraction)).thenReturn(verification);
        when(verification.verified()).thenReturn(
                Map.of(FacilityInvestmentFactDefinition.AMOUNT, verified)
        );
        when(verification.skipped()).thenReturn(Map.of());
        when(verified.evidence()).thenReturn(verifiedEvidence);
        when(factGenerator.generate(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                RECEIPT_NO,
                verification
        )).thenReturn(factSet);
        when(factSet.facts()).thenReturn(
                Map.of(FacilityInvestmentFactDefinition.AMOUNT, fact)
        );
        when(factSet.missingCoreDefinitions()).thenReturn(Set.of());
        when(persistenceService.replaceVerifiedFacts(
                DOCUMENT_ID,
                List.of(verifiedEvidence),
                List.of(fact)
        )).thenReturn(stored);

        FacilityInvestmentFactIngestionResult result =
                service.ingestByReceiptNo(RECEIPT_NO);

        assertThat(result.disclosureDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(result.candidateEvidenceCount()).isEqualTo(1);
        assertThat(result.verifiedEvidenceCount()).isEqualTo(1);
        assertThat(result.generatedFactCount()).isEqualTo(1);
        assertThat(result.hasAllCoreFacts()).isTrue();
        assertThat(result.extractionWarnings()).containsExactly("테스트 경고");

        ArgumentCaptor<Collection<DisclosureEvidence>> evidenceCaptor =
                ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<DisclosureFact>> factCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(persistenceService).replaceVerifiedFacts(
                org.mockito.ArgumentMatchers.eq(DOCUMENT_ID),
                evidenceCaptor.capture(),
                factCaptor.capture()
        );
        assertThat(evidenceCaptor.getValue()).containsExactly(verifiedEvidence);
        assertThat(factCaptor.getValue()).containsExactly(fact);
    }

    @Test
    void 검증된_Fact가_없으면_기존_적재값을_삭제하지_않는다() {
        Disclosure disclosure = disclosure();
        DisclosureDocument main = document(
                disclosure,
                DisclosureDocumentRole.MAIN,
                DisclosureDocumentParseStatus.COMPLETED
        );
        FacilityInvestmentEvidenceExtractionResult extraction =
                FacilityInvestmentEvidenceExtractionResult.empty(
                        "후보 없음"
                );
        FacilityInvestmentEvidenceVerificationResult verification = mock(
                FacilityInvestmentEvidenceVerificationResult.class
        );
        FacilityInvestmentFactSet factSet = mock(
                FacilityInvestmentFactSet.class
        );

        when(documentRepository
                .findAllByDisclosure_ReceiptNoOrderByIdAsc(RECEIPT_NO))
                .thenReturn(List.of(main));
        when(extractionService.extract(DOCUMENT_ID)).thenReturn(extraction);
        when(evidenceVerifier.verify(extraction)).thenReturn(verification);
        when(verification.verified()).thenReturn(Map.of());
        when(factGenerator.generate(
                DISCLOSURE_ID,
                DOCUMENT_ID,
                RECEIPT_NO,
                verification
        )).thenReturn(factSet);
        when(factSet.facts()).thenReturn(Map.of());

        assertThatThrownBy(() -> service.ingestByReceiptNo(RECEIPT_NO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기존 적재값을 교체하지 않습니다");

        verify(persistenceService, never()).replaceVerifiedFacts(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 파싱완료_MAIN_문서가_여러개면_임의선택하지_않는다() {
        Disclosure disclosure = disclosure();
        DisclosureDocument first = document(
                disclosure,
                DisclosureDocumentRole.MAIN,
                DisclosureDocumentParseStatus.COMPLETED
        );
        DisclosureDocument second = mock(DisclosureDocument.class);
        when(second.getDocumentRole()).thenReturn(DisclosureDocumentRole.MAIN);
        when(second.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.COMPLETED);

        when(documentRepository
                .findAllByDisclosure_ReceiptNoOrderByIdAsc(RECEIPT_NO))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.ingestByReceiptNo(RECEIPT_NO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("여러 개");

        verify(extractionService, never()).extract(
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Disclosure disclosure() {
        Disclosure disclosure = mock(Disclosure.class);
        when(disclosure.getId()).thenReturn(DISCLOSURE_ID);
        when(disclosure.getReceiptNo()).thenReturn(RECEIPT_NO);
        return disclosure;
    }

    private DisclosureDocument document(
            Disclosure disclosure,
            DisclosureDocumentRole role,
            DisclosureDocumentParseStatus parseStatus
    ) {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(document.getDocumentRole()).thenReturn(role);
        when(document.getParseStatus()).thenReturn(parseStatus);
        return document;
    }
}
