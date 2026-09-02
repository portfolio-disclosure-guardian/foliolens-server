package com.foliolens.backend.disclosure.domain.fact.facility;

import com.foliolens.backend.disclosure.domain.fact.AccountingBasis;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactGenerationMethod;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacilityInvestmentFactModelTest {

    private static final String RECEIPT_NO = "20240424800596";

    @Test
    void 승인된_Fact_Key와_공백이_다른_원문_레이블을_찾는다() {
        assertThat(FacilityInvestmentFactDefinition.fromFactKey(
                "facility.amount"
        )).contains(FacilityInvestmentFactDefinition.AMOUNT);
        assertThat(FacilityInvestmentFactDefinition.AMOUNT
                .matchesRowLabel("투자 금액")).isTrue();
        assertThat(FacilityInvestmentFactDefinition.START_DATE
                .matchesRowLabel("투자기간")).isTrue();
        assertThat(FacilityInvestmentFactDefinition.START_DATE
                .matchesColumnLabel("시작일")).isTrue();
        assertThat(FacilityInvestmentFactDefinition.START_DATE
                .matchesColumnLabel("종료일")).isFalse();
        assertThat(FacilityInvestmentFactDefinition.AMOUNT
                .matchesRowLabel("2. 투자금액(원)")).isTrue();
        assertThat(FacilityInvestmentFactDefinition.TARGET
                .matchesRowLabel("- 투자대상")).isTrue();
        assertThat(FacilityInvestmentFactDefinition.coreDefinitions())
                .hasSize(8)
                .doesNotContain(FacilityInvestmentFactDefinition.TYPE);
    }

    @Test
    void 같은_공시와_문서의_시설투자_Fact만_묶는다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DisclosureFact amount = amountFact(disclosureId, documentId);

        FacilityInvestmentFactSet factSet = new FacilityInvestmentFactSet(
                disclosureId,
                documentId,
                RECEIPT_NO,
                Map.of(FacilityInvestmentFactDefinition.AMOUNT, amount)
        );

        assertThat(factSet.find(FacilityInvestmentFactDefinition.AMOUNT))
                .contains(amount);
        assertThat(factSet.missingDefinitions())
                .contains(FacilityInvestmentFactDefinition.PURPOSE)
                .doesNotContain(FacilityInvestmentFactDefinition.AMOUNT);
        assertThat(factSet.hasAllDefinedFacts()).isFalse();
        assertThat(factSet.hasAllCoreFacts()).isFalse();
    }

    @Test
    void 정의와_다른_factKey는_시설투자_Fact_묶음에_넣을_수_없다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DisclosureFact amount = amountFact(disclosureId, documentId);

        assertThatThrownBy(() -> new FacilityInvestmentFactSet(
                disclosureId,
                documentId,
                RECEIPT_NO,
                Map.of(FacilityInvestmentFactDefinition.PURPOSE, amount)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factKey");
    }

    @Test
    void 다른_문서의_Fact는_같은_시설투자_Fact_묶음에_넣을_수_없다() {
        UUID disclosureId = UUID.randomUUID();
        DisclosureFact amount = amountFact(
                disclosureId,
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> new FacilityInvestmentFactSet(
                disclosureId,
                UUID.randomUUID(),
                RECEIPT_NO,
                Map.of(FacilityInvestmentFactDefinition.AMOUNT, amount)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공시·문서");
    }

    private DisclosureFact amountFact(
            UUID disclosureId,
            UUID documentId
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
                null,
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.VERIFIED,
                RECEIPT_NO,
                "facility-v1",
                List.of(UUID.randomUUID())
        );
    }
}
