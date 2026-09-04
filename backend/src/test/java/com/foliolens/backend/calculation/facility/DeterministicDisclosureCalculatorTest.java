package com.foliolens.backend.calculation.facility;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievedFact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicDisclosureCalculatorTest {

    private static final String DISCLOSURE_1 = "disclosure-1";
    private static final String DISCLOSURE_2 = "disclosure-2";

    private final DeterministicDisclosureCalculator calculator =
            new DeterministicDisclosureCalculator();

    @Test
    void 골든_케이스_비율을_재계산해_공시값과_일치를_판정한다() {
        // 공시비율 원문이 "9.90"(소수 둘째 자리)이므로 재계산값도 둘째 자리로
        // 반올림해 비교한다(docs/finance_domain/02.신규시설투자.md의
        // FACILITY_EQUITY_RATIO_CHECK).
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "5296200000000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "53503752397611"
                        ),
                        fact("ratio-1", "facility.equity_ratio", "9.90")
                )
        );

        assertThat(result.verdict()).isEqualTo(CalculationVerdict.MATCH);
        assertThat(result.displayValue()).isEqualTo("9.90");
        assertThat(result.disclosedValue()).isEqualTo("9.90");
        assertThat(result.unit()).isEqualTo("%");
        assertThat(result.inputFactIds())
                .containsExactly("amount-1", "equity-1");
    }

    @Test
    void 공시비율의_소수_자릿수가_다르면_반올림_기준도_따라간다() {
        // 같은 재계산값(약 9.8987%)이라도 공시 표시 자릿수가 1자리("9.9")면
        // 1자리로, 2자리("9.90")면 2자리로 반올림 기준이 달라진다.
        CalculationResult oneDigit = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "5296200000000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "53503752397611"
                        ),
                        fact("ratio-1", "facility.equity_ratio", "9.9")
                )
        );

        assertThat(oneDigit.displayValue()).isEqualTo("9.9");
        assertThat(oneDigit.verdict()).isEqualTo(CalculationVerdict.MATCH);
    }

    @Test
    void RATIO에서_서로_다른_factKey_비교조건은_요구하지_않는다() {
        CalculationResult result = calculator.calculate(
                command(false),
                List.of(
                        fact("amount-1", "facility.amount", "5296200000000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "53503752397611"
                        ),
                        fact("ratio-1", "facility.equity_ratio", "9.90")
                )
        );

        assertThat(result.verdict()).isEqualTo(CalculationVerdict.MATCH);
    }

    @Test
    void 분모가_0이면_계산불가를_반환한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("equity-1", "facility.equity_amount", "0")
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.rawResult()).isNull();
        assertThat(result.displayValue()).isNull();
        assertThat(result.verdictReason()).contains("0");
    }

    @Test
    void 입력이_누락되면_계산불가를_반환한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(fact("amount-1", "facility.amount", "1000"))
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("facility.equity_amount");
    }

    @Test
    void 검증되지_않은_Fact는_계산에_사용하지_않는다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "10000",
                                DISCLOSURE_1,
                                "KRW",
                                FactValidationStatus.UNVALIDATED
                        )
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("VERIFIED");
    }

    @Test
    void 다른_공시의_Fact는_함께_계산하지_않는다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "10000",
                                DISCLOSURE_2,
                                "KRW",
                                FactValidationStatus.VERIFIED
                        )
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("같은 공시");
    }

    @Test
    void KRW_단위가_아니면_계산하지_않는다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "10000",
                                DISCLOSURE_1,
                                "USD",
                                FactValidationStatus.VERIFIED
                        )
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("KRW");
    }

    @Test
    void 같은_factKey가_여러개면_임의로_선택하지_않는다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("amount-2", "facility.amount", "2000"),
                        fact("equity-1", "facility.equity_amount", "10000")
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("여러 개");
    }

    @Test
    void 공시비율_factKey가_여러개면_비교하지_않고_계산은_유지한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("equity-1", "facility.equity_amount", "10000"),
                        fact("ratio-1", "facility.equity_ratio", "10.0"),
                        fact("ratio-2", "facility.equity_ratio", "20.0")
                )
        );

        assertThat(result.verdict()).isEqualTo(CalculationVerdict.NOT_COMPARABLE);
        assertThat(result.rawResult()).isNotNull();
        assertThat(result.verdictReason()).contains("여러 개");
    }

    @Test
    void 자기자본이_음수이면_비교불가를_반환한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("equity-1", "facility.equity_amount", "-500"),
                        fact("ratio-1", "facility.equity_ratio", "-200.0")
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_COMPARABLE);
        assertThat(result.rawResult()).isNotNull();
        assertThat(result.displayValue()).isNotNull();
        assertThat(result.verdictReason()).contains("음수");
    }

    @Test
    void 공시값과_다르면_불일치를_반환한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "5296200000000"),
                        fact(
                                "equity-1",
                                "facility.equity_amount",
                                "53503752397611"
                        ),
                        fact("ratio-1", "facility.equity_ratio", "50.00")
                )
        );

        assertThat(result.verdict()).isEqualTo(CalculationVerdict.MISMATCH);
    }

    @Test
    void 비교할_공시값이_없으면_비교불가를_반환한다() {
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("equity-1", "facility.equity_amount", "10000")
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_COMPARABLE);
        assertThat(result.displayValue()).isEqualTo("10.0");
    }

    @Test
    void HALF_UP_반올림_경계값을_처리한다() {
        // 199 / 2000 * 100 = 9.95 (공시비율이 "10.0"으로 1자리이므로 그 기준으로
        // 반올림) -> HALF_UP으로 10.0
        CalculationResult result = calculator.calculate(
                command(true),
                List.of(
                        fact("amount-1", "facility.amount", "199"),
                        fact("equity-1", "facility.equity_amount", "2000"),
                        fact("ratio-1", "facility.equity_ratio", "10.0")
                )
        );

        assertThat(result.displayValue()).isEqualTo("10.0");
        assertThat(result.verdict()).isEqualTo(CalculationVerdict.MATCH);
    }

    @Test
    void 지원하지_않는_연산은_계산불가를_반환한다() {
        CalculationResult result = calculator.calculate(
                new CalculationCommand(
                        CalculationOperation.CHANGE_RATE,
                        new ComparisonBasis(true, true, true, true)
                ),
                List.of(
                        fact("amount-1", "facility.amount", "1000"),
                        fact("equity-1", "facility.equity_amount", "10000")
                )
        );

        assertThat(result.verdict())
                .isEqualTo(CalculationVerdict.NOT_CALCULABLE);
        assertThat(result.verdictReason()).contains("CHANGE_RATE");
    }

    private CalculationCommand command(boolean sameFactKey) {
        return new CalculationCommand(
                CalculationOperation.RATIO,
                new ComparisonBasis(true, sameFactKey, true, true)
        );
    }

    private RetrievedFact fact(
            String factId,
            String factKey,
            String normalizedValue
    ) {
        String unit = "facility.equity_ratio".equals(factKey)
                ? "PERCENT"
                : "KRW";
        return fact(
                factId,
                factKey,
                normalizedValue,
                DISCLOSURE_1,
                unit,
                FactValidationStatus.VERIFIED
        );
    }

    private RetrievedFact fact(
            String factId,
            String factKey,
            String normalizedValue,
            String disclosureId,
            String unit,
            FactValidationStatus validationStatus
    ) {
        return new RetrievedFact(
                factId,
                disclosureId,
                factKey,
                FactValueType.DECIMAL,
                normalizedValue,
                normalizedValue,
                unit,
                null,
                null,
                List.of(),
                validationStatus
        );
    }
}
