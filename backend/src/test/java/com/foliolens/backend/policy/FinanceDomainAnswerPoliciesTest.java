package com.foliolens.backend.policy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceDomainAnswerPoliciesTest {

    private static final Map<String, Integer> EXPECTED_FACT_COUNTS = Map.ofEntries(
            Map.entry("단일판매·공급계약", 66),
            Map.entry("신규시설투자", 49),
            Map.entry("투자판단 관련 주요경영사항", 87),
            Map.entry("주식 등의 대량보유상황보고서", 69),
            Map.entry("정기공시 재무·사업정보", 55),
            Map.entry("자기주식 취득·처분·신탁", 46),
            Map.entry("자금조달·자본변동", 126),
            Map.entry("합병·분할·주식교환", 90),
            Map.entry("자산·영업·지분거래", 130),
            Map.entry("계속기업·법률위험", 73),
            Map.entry("해외증권시장 상장·상장폐지", 59),
            Map.entry("정정·후속공시 및 기준시점 상태", 37)
    );

    private static final Map<String, Integer> EXPECTED_CALCULATION_COUNTS = Map.ofEntries(
            Map.entry("단일판매·공급계약", 5),
            Map.entry("신규시설투자", 14),
            Map.entry("투자판단 관련 주요경영사항", 7),
            Map.entry("주식 등의 대량보유상황보고서", 12),
            Map.entry("정기공시 재무·사업정보", 11),
            Map.entry("자기주식 취득·처분·신탁", 9),
            Map.entry("자금조달·자본변동", 10),
            Map.entry("합병·분할·주식교환", 18),
            Map.entry("자산·영업·지분거래", 18),
            Map.entry("계속기업·법률위험", 5),
            Map.entry("해외증권시장 상장·상장폐지", 14),
            Map.entry("정정·후속공시 및 기준시점 상태", 5)
    );

    @Test
    void financeDomain01To12DraftsMatchDocumentedPolicyShape() {
        var policies = FinanceDomainAnswerPolicies.all();

        assertEquals(12, policies.size());
        assertEquals(12, policies.stream().map(AnswerPolicy::disclosureSubtype).distinct().count());

        policies.forEach(policy -> {
            assertEquals(FinanceDomainAnswerPolicies.DRAFT_POLICY_VERSION, policy.policyVersion());
            assertEquals(EXPECTED_FACT_COUNTS.get(policy.disclosureSubtype()), policy.facts().size());
            assertEquals(EXPECTED_CALCULATION_COUNTS.get(policy.disclosureSubtype()), policy.calculations().size());
            assertEquals(policy.facts().size(), policy.facts().stream().map(FactPolicy::factKey).distinct().count());
            assertEquals(
                    policy.calculations().size(),
                    policy.calculations().stream().map(CalculationPolicy::calculationId).distinct().count());
            assertTrue(policy.facts().stream().allMatch(fact -> fact.necessity() == FactNecessity.SUPPORTING));
            assertTrue(policy.goldenCases().isEmpty());
        });
    }
}
