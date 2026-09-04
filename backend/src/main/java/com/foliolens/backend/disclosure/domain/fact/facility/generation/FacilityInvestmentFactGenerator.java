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
import java.util.Set;
import java.util.UUID;

/**
 * VERIFIED Evidence만 사용해 신규시설투자 {@link DisclosureFact}를 만든다.
 *
 * 원문값·원문단위·정규화값·정규화단위를 모두 보존하고, 같은 입력을 같은
 * 정책 버전으로 다시 넣으면 같은 {@code factId}가 만들어진다.
 */
public class FacilityInvestmentFactGenerator {

    public static final String POLICY_VERSION = "facility-fact-normalize-v1";

    /**
     * FOREIGN_VALUE/DISCLOSED_FX_RATE는 표 라벨의 "(원)" 같은 별도
     * 단위 셀이 아니라 "기타 투자판단과 관련한 중요사항" 서술 문장
     * 안에 단위가 함께 적혀 있어(예: "USD 1,118,534,000",
     * "1,263.1KRW/USD") {@link DisclosureEvidence#value()}의
     * rawUnit이 항상 비어 있다. {@link DisclosureFact}는 원문 기반
     * DECIMAL Fact에 rawUnit을 요구하므로, 이 두 Fact는 정규화 단계에서
     * 같은 원문 문장으로부터 이미 확정한 단위(normalizedUnit)를 그대로
     * rawUnit으로도 사용한다.
     */
    private static final Set<FacilityInvestmentFactDefinition>
            RAW_UNIT_FROM_NORMALIZED = Set.of(
                    FacilityInvestmentFactDefinition.FOREIGN_VALUE,
                    FacilityInvestmentFactDefinition.DISCLOSED_FX_RATE
            );

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
        String rawUnit = resolveRawUnit(definition, evidence, normalizedUnit);

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
                rawUnit,
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

    private String resolveRawUnit(
            FacilityInvestmentFactDefinition definition,
            DisclosureEvidence evidence,
            String normalizedUnit
    ) {
        if (evidence.value().rawUnit() != null) {
            return evidence.value().rawUnit();
        }
        return RAW_UNIT_FROM_NORMALIZED.contains(definition)
                ? normalizedUnit
                : null;
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
