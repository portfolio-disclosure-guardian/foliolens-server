package com.foliolens.backend.calculation.fake;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.DisclosureCalculator;
import com.foliolens.backend.policy.CalculationPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.retrieval.RetrievedFact;

// A5 fake 수직 연결: GOLD-FACILITY-001의 자기자본 대비 비율(facility.amount / facility.equity_amount)만
// 계산한다. 다른 factKey·operation은 아직 실제 DisclosureCalculator가 없으므로 다루지 않는다.
@Component
public final class FakeDisclosureCalculator implements DisclosureCalculator {

    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);

    @Override
    public CalculationResult calculate(CalculationCommand command, List<RetrievedFact> facts) {
        CalculationPolicy policy = GoldFacility001Fixture.policy().calculation();
        Map<String, RetrievedFact> byKey = facts.stream()
                .collect(Collectors.toMap(RetrievedFact::factKey, Function.identity()));
        RetrievedFact amount = byKey.get("facility.amount");
        RetrievedFact equity = byKey.get("facility.equity_amount");
        RetrievedFact disclosedRatio = byKey.get("facility.equity_ratio");

        if (amount == null || equity == null) {
            return new CalculationResult(command.operation(), List.of(), CalculationVerdict.NOT_CALCULABLE,
                    null, null, disclosedRatio != null ? disclosedRatio.normalizedValue() : null, "%",
                    "계산에 필요한 fact가 없습니다.");
        }

        BigDecimal amountValue = new BigDecimal(amount.normalizedValue());
        BigDecimal equityValue = new BigDecimal(equity.normalizedValue());
        BigDecimal raw = amountValue.divide(equityValue, MathContext.DECIMAL64).multiply(PERCENT_MULTIPLIER);
        BigDecimal display = raw.setScale(policy.displayScale(), policy.roundingMode());

        List<String> inputFactIds = List.of(amount.factId(), equity.factId());
        if (disclosedRatio == null) {
            return new CalculationResult(command.operation(), inputFactIds, CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(), display.toPlainString(), null, "%", "비교할 공시 기재값이 없습니다.");
        }

        boolean matches = display.compareTo(new BigDecimal(disclosedRatio.normalizedValue())) == 0;
        return new CalculationResult(command.operation(), inputFactIds,
                matches ? CalculationVerdict.MATCH : CalculationVerdict.MISMATCH,
                raw.doubleValue(), display.toPlainString(), disclosedRatio.normalizedValue(), "%",
                matches ? "공시값과 일치합니다." : "공시값과 다릅니다.");
    }
}
