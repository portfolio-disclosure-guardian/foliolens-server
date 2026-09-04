package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactSet;
import com.foliolens.backend.disclosure.domain.fact.facility.generation.FacilityInvestmentFactGenerator;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerificationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerifier;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.VerifiedFacilityEvidence;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceResult;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceService;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 시설투자 원문 한 건을 Evidence 후보 추출부터 VERIFIED Fact 저장까지
 * 연결하는 애플리케이션 서비스다.
 */
@Service
public class FacilityInvestmentFactIngestionService {

    private final DisclosureDocumentRepository documentRepository;
    private final FacilityInvestmentEvidenceExtractionService extractionService;
    private final FacilityInvestmentEvidenceVerifier evidenceVerifier;
    private final FacilityInvestmentFactGenerator factGenerator;
    private final DisclosureFactPersistenceService persistenceService;

    public FacilityInvestmentFactIngestionService(
            DisclosureDocumentRepository documentRepository,
            FacilityInvestmentEvidenceExtractionService extractionService,
            FacilityInvestmentEvidenceVerifier evidenceVerifier,
            FacilityInvestmentFactGenerator factGenerator,
            DisclosureFactPersistenceService persistenceService
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.extractionService = Objects.requireNonNull(
                extractionService,
                "extractionService는 필수입니다."
        );
        this.evidenceVerifier = Objects.requireNonNull(
                evidenceVerifier,
                "evidenceVerifier는 필수입니다."
        );
        this.factGenerator = Objects.requireNonNull(
                factGenerator,
                "factGenerator는 필수입니다."
        );
        this.persistenceService = Objects.requireNonNull(
                persistenceService,
                "persistenceService는 필수입니다."
        );
    }

    /** 접수번호에서 유일한 파싱 완료 MAIN 문서를 찾아 Fact를 교체 저장한다. */
    @Transactional
    public FacilityInvestmentFactIngestionResult ingestByReceiptNo(
            String receiptNo
    ) {
        String normalizedReceiptNo = requireReceiptNo(receiptNo);
        List<DisclosureDocument> targets = documentRepository
                .findAllByDisclosure_ReceiptNoOrderByIdAsc(normalizedReceiptNo)
                .stream()
                .filter(document -> document.getDocumentRole()
                        == DisclosureDocumentRole.MAIN)
                .filter(this::isParsed)
                .toList();

        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "파싱 완료된 MAIN 공시 문서를 찾을 수 없습니다. receiptNo="
                            + normalizedReceiptNo
            );
        }
        if (targets.size() > 1) {
            throw new IllegalStateException(
                    "접수번호에 파싱 완료 MAIN 문서가 여러 개입니다. receiptNo="
                            + normalizedReceiptNo
                            + ", count=" + targets.size()
            );
        }

        return ingest(targets.getFirst());
    }

    private FacilityInvestmentFactIngestionResult ingest(
            DisclosureDocument document
    ) {
        Disclosure disclosure = Objects.requireNonNull(
                document.getDisclosure(),
                "공시 문서의 Disclosure가 필요합니다."
        );

        FacilityInvestmentEvidenceExtractionResult extraction =
                extractionService.extract(document.getId());
        FacilityInvestmentEvidenceVerificationResult verification =
                evidenceVerifier.verify(extraction);
        FacilityInvestmentFactSet factSet = factGenerator.generate(
                disclosure.getId(),
                document.getId(),
                disclosure.getReceiptNo(),
                verification
        );

        List<DisclosureEvidence> verifiedEvidences = verification.verified()
                .values()
                .stream()
                .map(VerifiedFacilityEvidence::evidence)
                .toList();
        List<DisclosureFact> facts = List.copyOf(factSet.facts().values());

        if (facts.isEmpty()) {
            throw new IllegalStateException(
                    "검증된 시설투자 Fact가 없어 기존 적재값을 교체하지 않습니다. "
                            + "receiptNo=" + disclosure.getReceiptNo()
            );
        }

        DisclosureFactPersistenceResult persistenceResult =
                persistenceService.replaceVerifiedFacts(
                        document.getId(),
                        verifiedEvidences,
                        facts
                );

        return new FacilityInvestmentFactIngestionResult(
                disclosure.getId(),
                document.getId(),
                disclosure.getReceiptNo(),
                extraction.candidateCount(),
                verifiedEvidences.size(),
                facts.size(),
                factSet.missingCoreDefinitions(),
                verification.skipped(),
                extraction.warnings(),
                persistenceResult
        );
    }

    private boolean isParsed(DisclosureDocument document) {
        return document.getParseStatus() == DisclosureDocumentParseStatus.COMPLETED
                || document.getParseStatus()
                == DisclosureDocumentParseStatus.PARTIAL;
    }

    private String requireReceiptNo(String value) {
        if (value == null || !value.strip().matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }
        return value.strip();
    }
}
