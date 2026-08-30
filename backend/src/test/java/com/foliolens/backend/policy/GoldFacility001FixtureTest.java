package com.foliolens.backend.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// GOLD-FACILITY-001 fixture가 문서의 5.3~5.6절 계산·반올림·판정 규칙과 실제로 일치하는지 확인한다.
class GoldFacility001FixtureTest {

    @Test
    void requiredFactsAreAllPresentInGoldenCase() {
        AnswerPolicy policy = GoldFacility001Fixture.policy();

        policy.facts().forEach(fact ->
                assertTrue(
                        policy.goldenCase().expectedNormalizedFacts().containsKey(fact.factKey()),
                        fact.factKey() + "가 골든 케이스 기대값에 없습니다."
                )
        );
    }

    @Test
    void roundingRawResultReproducesDisclosedRatio() {
        AnswerPolicy policy = GoldFacility001Fixture.policy();
        CalculationPolicy calculation = policy.calculation();
        GoldenCase goldenCase = policy.goldenCase();

        BigDecimal rounded = new BigDecimal(goldenCase.expectedRawResult())
                .setScale(calculation.displayScale(), calculation.roundingMode());

        assertEquals(new BigDecimal(goldenCase.expectedDisplayValue()), rounded);
        assertEquals(
                goldenCase.expectedNormalizedFacts().get(calculation.disclosedValueFactKey()),
                goldenCase.expectedDisplayValue()
        );
    }

    // 문서의 raw result 문자열은 30자리까지 적혀 있지만, 그 정밀도로 재현하면
    // 문서 저자가 사용한 도구의 나머지 오차만 비교하게 된다. 표시 자릿수(2)보다
    // 충분히 깊은 15자리에서 일치하는지만 확인한다.
    private static final int COMPARISON_SCALE = 15;

    @Test
    void rawResultMatchesAmountDividedByEquity() {
        AnswerPolicy policy = GoldFacility001Fixture.policy();
        CalculationPolicy calculation = policy.calculation();
        GoldenCase goldenCase = policy.goldenCase();

        BigDecimal amount = new BigDecimal(
                goldenCase.expectedNormalizedFacts().get(calculation.numeratorFactKey())
        );
        BigDecimal equity = new BigDecimal(
                goldenCase.expectedNormalizedFacts().get(calculation.denominatorFactKey())
        );

        BigDecimal recomputed = amount
                .divide(equity, COMPARISON_SCALE + 2, calculation.roundingMode())
                .multiply(BigDecimal.valueOf(100))
                .setScale(COMPARISON_SCALE, calculation.roundingMode());

        BigDecimal expected = new BigDecimal(goldenCase.expectedRawResult())
                .setScale(COMPARISON_SCALE, calculation.roundingMode());

        assertEquals(expected, recomputed);
    }
}
