package com.foliolens.backend.disclosure.domain.fact;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisclosureFactTest {

    private static final String RECEIPT_NO = "20240424800596";

    @Test
    void 투자금액의_원문값과_KRW_정규화값을_함께_보존한다() {
        DisclosureFact fact = amountFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(UUID.randomUUID())
        );

        assertThat(fact.rawValue()).isEqualTo("5,296,200");
        assertThat(fact.rawUnit()).isEqualTo("백만원");
        assertThat(fact.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("5296200000000")
                ));
        assertThat(fact.normalizedUnit()).isEqualTo("KRW");
        assertThat(fact.available()).isTrue();
        assertThat(fact.verified()).isTrue();
    }

    @Test
    void normalizedValue의_타입은_valueType과_같아야_한다() {
        assertThatThrownBy(() -> fact(
                FactValueType.DECIMAL,
                new TextFactValue("숫자가 아님"),
                "백만원",
                "KRW",
                "KRW",
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.UNVALIDATED,
                List.of(UUID.randomUUID()),
                "facility-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("타입");
    }

    @Test
    void 원문_기반_VERIFIED_Fact에는_Evidence가_필요하다() {
        assertThatThrownBy(() -> amountFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evidence");
    }

    @Test
    void KRW_정규화_금액의_currency도_KRW여야_한다() {
        assertThatThrownBy(() -> fact(
                FactValueType.DECIMAL,
                new DecimalFactValue(new BigDecimal("5296200000000")),
                "백만원",
                "KRW",
                "USD",
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.UNVALIDATED,
                List.of(UUID.randomUUID()),
                "facility-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void AVAILABLE이_아닌_Fact에는_정규화값을_둘_수_없다() {
        assertThatThrownBy(() -> new DisclosureFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "facility.amount",
                FactValueType.DECIMAL,
                null,
                null,
                new DecimalFactValue(BigDecimal.ZERO),
                "KRW",
                "KRW",
                null,
                null,
                null,
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactAvailabilityStatus.NOT_STATED,
                FactNormalizationStatus.MISSING,
                FactValidationStatus.UNVALIDATED,
                RECEIPT_NO,
                "facility-v1",
                List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AVAILABLE이 아닌");
    }

    @Test
    void 투자목적_TEXT_Fact도_원문과_Evidence를_가진다() {
        UUID evidenceId = UUID.randomUUID();
        DisclosureFact fact = new DisclosureFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "facility.purpose",
                FactValueType.TEXT,
                "차세대 DRAM 생산능력 확장",
                null,
                new TextFactValue("차세대 DRAM 생산능력 확장"),
                null,
                null,
                null,
                null,
                null,
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_RAW,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.NOT_APPLICABLE,
                FactValidationStatus.VERIFIED,
                RECEIPT_NO,
                null,
                List.of(evidenceId)
        );

        assertThat(fact.evidenceIds()).containsExactly(evidenceId);
        assertThat(fact.normalizedValue())
                .isEqualTo(new TextFactValue("차세대 DRAM 생산능력 확장"));
    }

    private DisclosureFact amountFact(
            UUID disclosureId,
            UUID documentId,
            List<UUID> evidenceIds
    ) {
        return new DisclosureFact(
                UUID.randomUUID(),
                disclosureId,
                documentId,
                "facility.amount",
                FactValueType.DECIMAL,
                "5,296,200",
                "백만원",
                new DecimalFactValue(new BigDecimal("5296200000000")),
                "KRW",
                "KRW",
                null,
                null,
                LocalDate.of(2024, 4, 24),
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.VERIFIED,
                RECEIPT_NO,
                "facility-v1",
                evidenceIds
        );
    }

    private DisclosureFact fact(
            FactValueType valueType,
            FactValue normalizedValue,
            String rawUnit,
            String normalizedUnit,
            String currency,
            FactGenerationMethod generationMethod,
            FactNormalizationStatus normalizationStatus,
            FactValidationStatus validationStatus,
            List<UUID> evidenceIds,
            String policyVersion
    ) {
        return new DisclosureFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "facility.amount",
                valueType,
                "5,296,200",
                rawUnit,
                normalizedValue,
                normalizedUnit,
                currency,
                null,
                null,
                null,
                AccountingBasis.UNKNOWN,
                generationMethod,
                FactAvailabilityStatus.AVAILABLE,
                normalizationStatus,
                validationStatus,
                RECEIPT_NO,
                policyVersion,
                evidenceIds
        );
    }
}
