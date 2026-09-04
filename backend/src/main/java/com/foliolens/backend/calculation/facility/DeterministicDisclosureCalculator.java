package com.foliolens.backend.calculation.facility;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.DisclosureCalculator;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievedFact;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 *
 * {@code docs/finance_domain/02.신규시설투자.md}의
 * {@code FACILITY_EQUITY_RATIO_CHECK} 규칙에 따라 반올림 자릿수는 고정값이
 * 아니라 공시비율(facility.equity_ratio) 원문의 소수 자릿수 {@code d}를
 * 따른다. 공시비율을 신뢰할 수 없거나 없으면 {@link #DEFAULT_DISPLAY_SCALE}로
 * 대체 표시하되 비교는 하지 않는다({@code NOT_COMPARABLE}).
 *
 * {@link RetrievedFact}는 아직 {@code accountingBasis}(연결·별도 기준)를
 * 담지 않으므로(주석 참고) 그 기준 검증은 현재 이 계산기가 확인할 수
 * 없다. 이는 공통 계약 확장이 필요한 부분으로 남겨둔다.
 *
 * fake-calculation 프로필이 아닌 모든 실행(기본·evaluation 포함)에서
 * {@link DisclosureCalculator} Bean으로 활성화된다. fake-calculation
 * 프로필에서는 대신
 * {@link com.foliolens.backend.calculation.fake.FakeDisclosureCalculator}가
 * 활성화되므로 두 구현체가 동시에 Bean으로 등록되는 일은 없다.
 */
@Component
@Profile("!fake-calculation")
public class DeterministicDisclosureCalculator implements DisclosureCalculator {

    private static final String AMOUNT_FACT_KEY = "facility.amount";
    private static final String EQUITY_AMOUNT_FACT_KEY = "facility.equity_amount";
    private static final String EQUITY_RATIO_FACT_KEY = "facility.equity_ratio";
    private static final String KRW_UNIT = "KRW";
    private static final String PERCENT_UNIT = "PERCENT";

    // 공시비율을 신뢰할 수 없어 원문 소수 자릿수(d)를 알 수 없을 때만 쓰는
    // 표시용 기본 자릿수. 이 경우 비교는 하지 않으므로(NOT_COMPARABLE)
    // 판정에는 영향을 주지 않는다.
    private static final int DEFAULT_DISPLAY_SCALE = 1;
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

        Map<String, List<RetrievedFact>> byKey = indexByFactKey(facts);
        List<RetrievedFact> amountCandidates =
                byKey.getOrDefault(AMOUNT_FACT_KEY, List.of());
        List<RetrievedFact> equityCandidates =
                byKey.getOrDefault(EQUITY_AMOUNT_FACT_KEY, List.of());
        List<RetrievedFact> ratioCandidates =
                byKey.getOrDefault(EQUITY_RATIO_FACT_KEY, List.of());

        if (amountCandidates.size() > 1 || equityCandidates.size() > 1) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    "같은 factKey의 Fact가 여러 개라 임의로 선택할 수 없습니다: "
                            + AMOUNT_FACT_KEY + ", " + EQUITY_AMOUNT_FACT_KEY
            );
        }

        RetrievedFact amount = amountCandidates.isEmpty()
                ? null : amountCandidates.get(0);
        RetrievedFact equity = equityCandidates.isEmpty()
                ? null : equityCandidates.get(0);

        if (amount == null || amount.normalizedValue() == null
                || equity == null || equity.normalizedValue() == null) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    "계산에 필요한 facility.amount 또는 "
                            + "facility.equity_amount가 없습니다."
            );
        }

        List<String> inputFactIds = List.of(amount.factId(), equity.factId());

        if (amount.validationStatus() != FactValidationStatus.VERIFIED
                || equity.validationStatus() != FactValidationStatus.VERIFIED) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "VERIFIED 상태가 아닌 Fact는 계산에 사용할 수 없습니다."
            );
        }

        if (!Objects.equals(amount.disclosureId(), equity.disclosureId())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "투자금액과 자기자본이 같은 공시의 Fact가 아닙니다."
            );
        }

        if (!KRW_UNIT.equals(amount.unit()) || !KRW_UNIT.equals(equity.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "투자금액과 자기자본이 모두 KRW 단위가 아닙니다."
            );
        }

        if (!samePeriod(amount, equity)) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "투자금액과 자기자본의 기준시점(기간)이 다릅니다."
            );
        }

        BigDecimal amountValue;
        BigDecimal equityValue;
        try {
            amountValue = new BigDecimal(amount.normalizedValue());
            equityValue = new BigDecimal(equity.normalizedValue());
        } catch (NumberFormatException e) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "정규화된 금액을 숫자로 변환할 수 없습니다."
            );
        }

        if (equityValue.signum() == 0) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    "자기자본(분모)이 0입니다."
            );
        }

        DisclosedRatio disclosedRatio = resolveDisclosedRatio(
                ratioCandidates,
                amount
        );

        int displayScale = disclosedRatio.usable()
                ? Math.max(disclosedRatio.number().scale(), 0)
                : DEFAULT_DISPLAY_SCALE;

        BigDecimal raw = amountValue
                .divide(equityValue, MathContext.DECIMAL64)
                .multiply(PERCENT_MULTIPLIER);
        BigDecimal display = raw.setScale(displayScale, ROUNDING_MODE);

        if (equityValue.signum() < 0) {
            return new CalculationResult(
                    command.operation(),
                    inputFactIds,
                    CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(),
                    display.toPlainString(),
                    disclosedRatio.rawValue(),
                    UNIT,
                    "자기자본이 음수여서 정상적으로 비교할 수 없습니다."
            );
        }

        if (!disclosedRatio.usable()) {
            return new CalculationResult(
                    command.operation(),
                    inputFactIds,
                    CalculationVerdict.NOT_COMPARABLE,
                    raw.doubleValue(),
                    display.toPlainString(),
                    disclosedRatio.rawValue(),
                    UNIT,
                    disclosedRatio.reason()
            );
        }

        BigDecimal disclosedRounded =
                disclosedRatio.number().setScale(displayScale, ROUNDING_MODE);
        boolean matches = display.compareTo(disclosedRounded) == 0;

        return new CalculationResult(
                command.operation(),
                inputFactIds,
                matches ? CalculationVerdict.MATCH : CalculationVerdict.MISMATCH,
                raw.doubleValue(),
                display.toPlainString(),
                disclosedRatio.rawValue(),
                UNIT,
                matches
                        ? "재계산값과 공시값이 공시 소수 자릿수(" + displayScale
                                + "자리) 반올림 기준으로 일치합니다."
                        : "재계산값과 공시값이 공시 소수 자릿수(" + displayScale
                                + "자리) 반올림 기준으로 다릅니다."
        );
    }

    /**
     * 공시비율(facility.equity_ratio) 후보를 비교에 사용할 수 있는지
     * 판정한다. 후보가 없거나, 여러 개라 모호하거나, 검증되지 않았거나,
     * 다른 공시의 값이거나, 단위가 PERCENT가 아니거나, 숫자로 변환할 수
     * 없으면 비교에 사용하지 않는다(NOT_COMPARABLE 사유만 남긴다).
     */
    private DisclosedRatio resolveDisclosedRatio(
            List<RetrievedFact> ratioCandidates,
            RetrievedFact amount
    ) {
        if (ratioCandidates.isEmpty()) {
            return DisclosedRatio.unusable(null, "비교할 공시 기재값이 없습니다.");
        }
        if (ratioCandidates.size() > 1) {
            return DisclosedRatio.unusable(
                    null,
                    "같은 factKey(" + EQUITY_RATIO_FACT_KEY
                            + ")의 공시비율 Fact가 여러 개라 임의로 선택할 수 없습니다."
            );
        }

        RetrievedFact ratio = ratioCandidates.get(0);
        String rawValue = ratio.normalizedValue();

        if (ratio.validationStatus() != FactValidationStatus.VERIFIED) {
            return DisclosedRatio.unusable(
                    rawValue,
                    "VERIFIED 상태가 아닌 공시비율은 비교에 사용할 수 없습니다."
            );
        }
        if (!Objects.equals(amount.disclosureId(), ratio.disclosureId())) {
            return DisclosedRatio.unusable(
                    rawValue,
                    "공시비율이 투자금액과 같은 공시의 Fact가 아닙니다."
            );
        }
        if (!PERCENT_UNIT.equals(ratio.unit())) {
            return DisclosedRatio.unusable(
                    rawValue,
                    "공시비율의 단위가 PERCENT가 아닙니다."
            );
        }
        if (rawValue == null) {
            return DisclosedRatio.unusable(null, "비교할 공시 기재값이 없습니다.");
        }

        try {
            return DisclosedRatio.usable(rawValue, new BigDecimal(rawValue));
        } catch (NumberFormatException e) {
            return DisclosedRatio.unusable(
                    rawValue,
                    "공시 기재값을 숫자로 변환할 수 없습니다."
            );
        }
    }

    private boolean samePeriod(RetrievedFact left, RetrievedFact right) {
        return Objects.equals(left.periodStart(), right.periodStart())
                && Objects.equals(left.periodEnd(), right.periodEnd());
    }

    private Map<String, List<RetrievedFact>> indexByFactKey(
            List<RetrievedFact> facts
    ) {
        Map<String, List<RetrievedFact>> byKey = new HashMap<>();
        for (RetrievedFact fact : facts) {
            if (fact != null && fact.factKey() != null) {
                byKey.computeIfAbsent(
                        fact.factKey(),
                        ignored -> new ArrayList<>()
                ).add(fact);
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

    private record DisclosedRatio(
            boolean usable,
            String rawValue,
            BigDecimal number,
            String reason
    ) {
        static DisclosedRatio usable(String rawValue, BigDecimal number) {
            return new DisclosedRatio(true, rawValue, number, null);
        }

        static DisclosedRatio unusable(String rawValue, String reason) {
            return new DisclosedRatio(false, rawValue, null, reason);
        }
    }
}
