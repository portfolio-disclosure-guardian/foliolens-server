package com.foliolens.backend.disclosure.domain.fact;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 원문값, 타입이 보존된 정규화값, 검증 상태와 Evidence를 한 단위로
 * 관리하는 공통 Fact envelope.
 */
public record DisclosureFact(
        UUID factId,
        UUID disclosureId,
        UUID disclosureDocumentId,
        String factKey,
        FactValueType valueType,
        String rawValue,
        String rawUnit,
        FactValue normalizedValue,
        String normalizedUnit,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOfDate,
        AccountingBasis accountingBasis,
        FactGenerationMethod generationMethod,
        FactAvailabilityStatus availabilityStatus,
        FactNormalizationStatus normalizationStatus,
        FactValidationStatus validationStatus,
        String sourceReceiptNo,
        String policyVersion,
        List<UUID> evidenceIds
) {

    public DisclosureFact {
        factId = requireId(factId, "factId");
        disclosureId = requireId(disclosureId, "disclosureId");
        disclosureDocumentId = requireId(
                disclosureDocumentId,
                "disclosureDocumentId"
        );
        factKey = requireFactKey(factKey);
        valueType = Objects.requireNonNull(
                valueType,
                "valueType은 필수입니다."
        );
        rawValue = normalizeOptionalText(rawValue);
        rawUnit = normalizeOptionalText(rawUnit);
        normalizedUnit = normalizeOptionalText(normalizedUnit);
        currency = normalizeOptionalText(currency);
        accountingBasis = Objects.requireNonNull(
                accountingBasis,
                "accountingBasis는 필수입니다."
        );
        generationMethod = Objects.requireNonNull(
                generationMethod,
                "generationMethod는 필수입니다."
        );
        availabilityStatus = Objects.requireNonNull(
                availabilityStatus,
                "availabilityStatus는 필수입니다."
        );
        normalizationStatus = Objects.requireNonNull(
                normalizationStatus,
                "normalizationStatus는 필수입니다."
        );
        validationStatus = Objects.requireNonNull(
                validationStatus,
                "validationStatus는 필수입니다."
        );
        sourceReceiptNo = requireReceiptNo(sourceReceiptNo);
        policyVersion = normalizeOptionalText(policyVersion);
        evidenceIds = immutableEvidenceIds(evidenceIds);

        validatePeriod(periodStart, periodEnd);
        validateNormalizedValue(valueType, normalizedValue);
        validateAvailability(
                availabilityStatus,
                rawValue,
                normalizedValue
        );
        validateSourceValue(
                valueType,
                generationMethod,
                availabilityStatus,
                rawValue,
                rawUnit
        );
        validateNormalizedUnit(valueType, normalizedValue, normalizedUnit);
        validateCurrency(normalizedUnit, currency);
        validatePolicyVersion(generationMethod, policyVersion);
        validateVerifiedFact(
                generationMethod,
                availabilityStatus,
                normalizationStatus,
                validationStatus,
                normalizedValue,
                evidenceIds
        );
    }

    public boolean available() {
        return availabilityStatus == FactAvailabilityStatus.AVAILABLE;
    }

    public boolean verified() {
        return validationStatus == FactValidationStatus.VERIFIED;
    }

    private static void validateNormalizedValue(
            FactValueType valueType,
            FactValue normalizedValue
    ) {
        if (normalizedValue != null
                && normalizedValue.valueType() != valueType) {
            throw new IllegalArgumentException(
                    "normalizedValue의 타입이 valueType과 다릅니다."
            );
        }
    }

    private static void validateAvailability(
            FactAvailabilityStatus availabilityStatus,
            String rawValue,
            FactValue normalizedValue
    ) {
        if (availabilityStatus != FactAvailabilityStatus.AVAILABLE
                && normalizedValue != null) {
            throw new IllegalArgumentException(
                    "AVAILABLE이 아닌 Fact에는 normalizedValue를 "
                            + "지정할 수 없습니다."
            );
        }
        if (availabilityStatus == FactAvailabilityStatus.AVAILABLE
                && rawValue == null
                && normalizedValue == null) {
            throw new IllegalArgumentException(
                    "AVAILABLE Fact에는 원문값 또는 정규화값이 필요합니다."
            );
        }
    }

    private static void validateSourceValue(
            FactValueType valueType,
            FactGenerationMethod generationMethod,
            FactAvailabilityStatus availabilityStatus,
            String rawValue,
            String rawUnit
    ) {
        if (availabilityStatus != FactAvailabilityStatus.AVAILABLE
                || !sourceBased(generationMethod)) {
            return;
        }
        if (rawValue == null) {
            throw new IllegalArgumentException(
                    "원문 기반 AVAILABLE Fact에는 rawValue가 필요합니다."
            );
        }
        if (valueType == FactValueType.DECIMAL && rawUnit == null) {
            throw new IllegalArgumentException(
                    "원문 기반 DECIMAL Fact에는 rawUnit이 필요합니다."
            );
        }
    }

    private static void validateNormalizedUnit(
            FactValueType valueType,
            FactValue normalizedValue,
            String normalizedUnit
    ) {
        if (normalizedValue != null
                && valueType == FactValueType.DECIMAL
                && normalizedUnit == null) {
            throw new IllegalArgumentException(
                    "정규화된 DECIMAL Fact에는 normalizedUnit이 필요합니다."
            );
        }
    }

    private static void validateCurrency(
            String normalizedUnit,
            String currency
    ) {
        if ("KRW".equals(normalizedUnit)
                && !"KRW".equals(currency)) {
            throw new IllegalArgumentException(
                    "KRW 금액 Fact의 currency는 KRW여야 합니다."
            );
        }
    }

    private static void validatePolicyVersion(
            FactGenerationMethod generationMethod,
            String policyVersion
    ) {
        boolean requiresPolicy = switch (generationMethod) {
            case DIRECT_NORMALIZED,
                    DERIVED_CLASSIFICATION,
                    DERIVED_CALCULATION,
                    LINKED_RESOLVED -> true;
            default -> false;
        };
        if (requiresPolicy && policyVersion == null) {
            throw new IllegalArgumentException(
                    generationMethod + " Fact에는 policyVersion이 필요합니다."
            );
        }
    }

    private static void validateVerifiedFact(
            FactGenerationMethod generationMethod,
            FactAvailabilityStatus availabilityStatus,
            FactNormalizationStatus normalizationStatus,
            FactValidationStatus validationStatus,
            FactValue normalizedValue,
            List<UUID> evidenceIds
    ) {
        if (validationStatus != FactValidationStatus.VERIFIED) {
            return;
        }
        if (availabilityStatus == FactAvailabilityStatus.AVAILABLE
                && normalizedValue == null) {
            throw new IllegalArgumentException(
                    "VERIFIED AVAILABLE Fact에는 normalizedValue가 필요합니다."
            );
        }
        if (normalizationStatus == FactNormalizationStatus.UNMAPPED
                || normalizationStatus == FactNormalizationStatus.AMBIGUOUS
                || normalizationStatus
                == FactNormalizationStatus.REVIEW_REQUIRED
                || normalizationStatus == FactNormalizationStatus.MISSING) {
            throw new IllegalArgumentException(
                    "VERIFIED Fact에 사용할 수 없는 normalizationStatus입니다: "
                            + normalizationStatus
            );
        }
        if (requiresEvidence(generationMethod) && evidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "원문 기반 VERIFIED Fact에는 Evidence가 필요합니다."
            );
        }
    }

    private static boolean sourceBased(
            FactGenerationMethod generationMethod
    ) {
        return generationMethod != FactGenerationMethod.DERIVED_CALCULATION
                && generationMethod != FactGenerationMethod.SYSTEM_ASSIGNED;
    }

    private static boolean requiresEvidence(
            FactGenerationMethod generationMethod
    ) {
        return sourceBased(generationMethod);
    }

    private static void validatePeriod(
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if ((periodStart == null) != (periodEnd == null)) {
            throw new IllegalArgumentException(
                    "periodStart와 periodEnd는 함께 존재해야 합니다."
            );
        }
        if (periodStart != null && periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException(
                    "periodStart는 periodEnd보다 뒤일 수 없습니다."
            );
        }
    }

    private static List<UUID> immutableEvidenceIds(List<UUID> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "evidenceIds에는 null이 포함될 수 없습니다."
            );
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) {
            throw new IllegalArgumentException(
                    "evidenceIds에는 중복 ID가 포함될 수 없습니다."
            );
        }
        return List.copyOf(unique);
    }

    private static UUID requireId(UUID value, String fieldName) {
        return Objects.requireNonNull(
                value,
                fieldName + "는 필수입니다."
        );
    }

    private static String requireFactKey(String value) {
        String normalized = requireText(value, "factKey");
        if (!normalized.matches(
                "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"
        )) {
            throw new IllegalArgumentException(
                    "factKey 형식이 올바르지 않습니다: " + normalized
            );
        }
        return normalized;
    }

    private static String requireReceiptNo(String value) {
        String normalized = requireText(value, "sourceReceiptNo");
        if (!normalized.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "sourceReceiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "는 비어 있을 수 없습니다."
            );
        }
        return value.strip();
    }
}
