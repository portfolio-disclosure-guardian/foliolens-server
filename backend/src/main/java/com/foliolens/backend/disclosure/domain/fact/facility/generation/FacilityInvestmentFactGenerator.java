package com.foliolens.backend.disclosure.domain.fact.facility.generation;

import com.foliolens.backend.disclosure.domain.fact.AccountingBasis;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactGenerationMethod;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactSet;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FactValueNormalizationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerificationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.VerifiedFacilityEvidence;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * VERIFIED Evidence만 사용해 신규시설투자 {@link DisclosureFact}를 만든다.
 *
 * 원문값·원문단위·정규화값·정규화단위를 모두 보존하고, 같은 입력을 같은
 * 정책 버전으로 다시 넣으면 같은 {@code factId}가 만들어진다.
 */
public class FacilityInvestmentFactGenerator {

    public static final String POLICY_VERSION = "facility-fact-normalize-v1";

    public FacilityInvestmentFactSet generate(
            UUID disclosureId,
            UUID disclosureDocumentId,
            String receiptNo,
            FacilityInvestmentEvidenceVerificationResult verification
    ) {
        Objects.requireNonNull(disclosureId, "disclosureId는 필수입니다.");
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        Objects.requireNonNull(receiptNo, "receiptNo는 필수입니다.");
        Objects.requireNonNull(verification, "verification은 필수입니다.");

        Map<FacilityInvestmentFactDefinition, DisclosureFact> facts =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);

        for (FacilityInvestmentFactDefinition definition
                : FacilityInvestmentFactDefinition.values()) {
            verification.find(definition).ifPresent(verifiedEvidence ->
                    facts.put(
                            definition,
                            buildFact(
                                    disclosureId,
                                    disclosureDocumentId,
                                    receiptNo,
                                    verifiedEvidence
                            )
                    )
            );
        }

        return new FacilityInvestmentFactSet(
                disclosureId,
                disclosureDocumentId,
                receiptNo,
                facts
        );
    }

    private DisclosureFact buildFact(
            UUID disclosureId,
            UUID disclosureDocumentId,
            String receiptNo,
            VerifiedFacilityEvidence verifiedEvidence
    ) {
        FacilityInvestmentFactDefinition definition =
                verifiedEvidence.definition();
        DisclosureEvidence evidence = verifiedEvidence.evidence();
        FactValueNormalizationResult normalization =
                verifiedEvidence.normalization();

        requireSameSource(disclosureId, disclosureDocumentId, receiptNo, evidence);

        String normalizedUnit = normalization.normalizedUnit();
        String currency = "KRW".equals(normalizedUnit) ? "KRW" : null;

        return new DisclosureFact(
                deterministicFactId(
                        disclosureDocumentId,
                        evidence.evidenceId(),
                        definition.factKey()
                ),
                disclosureId,
                disclosureDocumentId,
                definition.factKey(),
                definition.valueType(),
                evidence.value().rawValue(),
                evidence.value().rawUnit(),
                normalization.normalizedValue(),
                normalizedUnit,
                currency,
                null,
                null,
                null,
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.VERIFIED,
                receiptNo,
                POLICY_VERSION,
                List.of(evidence.evidenceId())
        );
    }

    private void requireSameSource(
            UUID disclosureId,
            UUID disclosureDocumentId,
            String receiptNo,
            DisclosureEvidence evidence
    ) {
        if (!disclosureId.equals(evidence.disclosureId())
                || !disclosureDocumentId.equals(
                        evidence.disclosureDocumentId()
                )
                || !receiptNo.equals(evidence.receiptNo())) {
            throw new IllegalArgumentException(
                    "Evidence의 공시·문서·접수번호가 생성 대상과 다릅니다. "
                            + "evidenceId=" + evidence.evidenceId()
            );
        }
    }

    private UUID deterministicFactId(
            UUID disclosureDocumentId,
            UUID evidenceId,
            String factKey
    ) {
        String key = disclosureDocumentId
                + "|" + evidenceId
                + "|" + factKey
                + "|" + POLICY_VERSION;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
