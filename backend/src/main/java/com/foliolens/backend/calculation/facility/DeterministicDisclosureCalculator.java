package com.foliolens.backend.calculation.facility;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.DisclosureCalculator;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievedFact;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code facility.amount ÷ facility.equity_amount × 100} 자기자본 대비
 * 비율만 결정적으로 계산하는 {@link DisclosureCalculator} 구현체.
 *
 * 제출 전 필수 연산인 RATIO만 실제로 지원한다. 다른 연산은 잘못
 * 계산하지 않고 이유와 함께 {@link CalculationVerdict#NOT_CALCULABLE}을
 * 반환한다. RATIO의 분자·분모는 서로 다른 factKey를 갖는 것이 정상이므로
 * {@link CalculationCommand#comparisonBasis()}의 sameFactKey 조건은
 * 검사하지 않는다.
 */
public class DeterministicDisclosureCalculator implements DisclosureCalculator {

    private static final String AMOUNT_FACT_KEY = "facility.amount";
    private static final String EQUITY_AMOUNT_FACT_KEY = "facility.equity_amount";
    private static final String EQUITY_RATIO_FACT_KEY = "facility.equity_ratio";
    private static final int DISPLAY_SCALE = 1;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final String UNIT = "%";

    @Override
    public CalculationResult calculate(
            CalculationCommand command,
            List<RetrievedFact> facts
    ) {
        Objects.requireNonNull(command, "command는 필수입니다.");
        Objects.requireNonNull(facts, "facts는 필수입니다.");

        if (command.operation() != CalculationOperation.RATIO) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    "아직 지원하지 않는 연산입니다: " + command.operation()
            );
        }

        Map<String, RetrievedFact> byKey = indexByFactKey(facts);
        RetrievedFact amount = byKey.get(AMOUNT_FACT_KEY);
        RetrievedFact equity = byKey.get(EQUITY_AMOUNT_FACT_KEY);
        RetrievedFact disclosedRatio = byKey.get(EQUITY_RATIO_FACT_KEY);
        String disclosedValue = disclosedRatio == null
                ? null
                : disclosedRatio.normalizedValue();

        if (amount == null || amount.normalizedValue() == null
                || equity == null || equity.normalizedValue() == null) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    disclosedValue,
                    "계산에 필요한 facility.amount 또는 "
                            + "facility.equity_amount가 없습니다."
            );
        }

        List<String> inputFactIds = List.of(amount.factId(), equity.factId());

        BigDecimal amountValue;
        BigDecimal equityValue;
        try {
            amountValue = new BigDecimal(amount.normalizedValue());
            equityValue = new BigDecimal(equity.normalizedValue());
        } catch (NumberFormatException e) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    disclosedValue,
                    "정규화된 금액을 숫자로 변환할 수 없습니다."
            );
        }

        if (equityValue.signum() == 0) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    disclosedValue,
                    "자기자본(분모)이 0입니다."
            );
        }

        BigDecimal raw = amountValue
                .divide(equityValue, MathContext.DECIMAL64)
                .multiply(PERCENT_MULTIPLIER);
        BigDecimal display = raw.setScale(DISPLAY_SCALE, ROUNDING_MODE);

        if (equityValue.signum() < 0) {
            return new CalculationResult(
                    command.operation(),
                    inputFactIds,
                    CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(),
                    display.toPlainString(),
                    disclosedValue,
                    UNIT,
                    "자기자본이 음수여서 정상적으로 비교할 수 없습니다."
            );
        }

        if (disclosedValue == null) {
            return new CalculationResult(
                    command.operation(),
                    inputFactIds,
                    CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(),
                    display.toPlainString(),
                    null,
                    UNIT,
                    "비교할 공시 기재값이 없습니다."
            );
        }

        BigDecimal disclosedNumber;
        try {
            disclosedNumber = new BigDecimal(disclosedValue);
        } catch (NumberFormatException e) {
            return new CalculationResult(
                    command.operation(),
                    inputFactIds,
                    CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(),
                    display.toPlainString(),
                    disclosedValue,
                    UNIT,
                    "공시 기재값을 숫자로 변환할 수 없습니다."
            );
        }

        BigDecimal disclosedRounded =
                disclosedNumber.setScale(DISPLAY_SCALE, ROUNDING_MODE);
        boolean matches = display.compareTo(disclosedRounded) == 0;

        return new CalculationResult(
                command.operation(),
                inputFactIds,
                matches ? CalculationVerdict.MATCH : CalculationVerdict.MISMATCH,
                raw.doubleValue(),
                display.toPlainString(),
                disclosedValue,
                UNIT,
                matches
                        ? "재계산값과 공시값이 반올림 기준으로 일치합니다."
                        : "재계산값과 공시값이 반올림 기준으로 다릅니다."
        );
    }

    private Map<String, RetrievedFact> indexByFactKey(List<RetrievedFact> facts) {
        Map<String, RetrievedFact> byKey = new HashMap<>();
        for (RetrievedFact fact : facts) {
            if (fact != null && fact.factKey() != null) {
                byKey.putIfAbsent(fact.factKey(), fact);
            }
        }
        return byKey;
    }

    private CalculationResult notCalculable(
            CalculationOperation operation,
            List<String> inputFactIds,
            String disclosedValue,
            String reason
    ) {
        return new CalculationResult(
                operation,
                inputFactIds,
                CalculationVerdict.NOT_CALCULABLE,
                null,
                null,
                disclosedValue,
                UNIT,
                reason
        );
    }
}
