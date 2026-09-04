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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link DisclosureCalculator}의 결정적 구현체.
 *
 * 제출 전 필수 연산인 {@code RATIO}
 * ({@code facility.amount ÷ facility.equity_amount × 100})와,
 * 새 Fact 추출 없이 기존 핵심 Fact만으로 계산 가능한
 * {@code DATE_DURATION}({@code facility.end_date − facility.start_date + 1}일)
 * 만 실제로 지원한다. 다른 연산은 잘못 계산하지 않고 이유와 함께
 * {@link CalculationVerdict#NOT_CALCULABLE}을 반환한다. RATIO의
 * 분자·분모는 서로 다른 factKey를 갖는 것이 정상이므로
 * {@link CalculationCommand#comparisonBasis()}의 sameFactKey 조건은
 * 검사하지 않는다.
 *
 * {@code docs/finance_domain/02.신규시설투자.md}의
 * {@code FACILITY_EQUITY_RATIO_CHECK} 규칙에 따라 반올림 자릿수는 고정값이
 * 아니라 공시비율(facility.equity_ratio) 원문의 소수 자릿수 {@code d}를
 * 따른다. 공시비율을 신뢰할 수 없거나 없으면 {@link #DEFAULT_DISPLAY_SCALE}로
 * 대체 표시하되 비교는 하지 않는다({@code NOT_COMPARABLE}).
 *
 * {@code FACILITY_DURATION_DAYS}는 공시에 별도의 "총 투자기간" 값이 없어
 * 비교 대상이 없으므로, 계산이 성공해도 항상 {@code NOT_COMPARABLE}로
 * 계산값만 제공한다.
 *
 * {@code PRODUCT}는 {@code FACILITY_FX_CHECK}
 * ({@code facility.amount.foreign_value × facility.amount.disclosed_fx_rate}를
 * {@code facility.amount}와 비교)만 지원한다. 두 Fact 모두
 * "기타 투자판단과 관련한 중요사항" 서술 문장에서 정규식으로 뽑아낸
 * 값이라 43건 실제 코퍼스 기준 USD로 표시된 문서에만 존재하며, 문장에
 * 외화 원금이나 환율 숫자가 없으면(예: "USD환율을 적용한 금액임"처럼
 * 통화만 언급) 추측하지 않고 {@code NOT_CALCULABLE}로 남는다. 총사업비
 * ×회사부담률({@code FACILITY_COMPANY_SHARE_CHECK})은 43건 어디에도
 * 해당 값이 없어 Fact 자체를 추출하지 않았으므로, {@code PRODUCT}가
 * 이 계산까지 대신 지원하지는 않는다.
 *
 * 다른 "동일 공시 계산"({@code AMOUNT_CHANGE}, {@code SCHEDULE_DELAY} 등)은
 * 정정 전/후 값 Fact가 아직 추출되지 않아 지원하지 않는다.
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
    private static final String START_DATE_FACT_KEY = "facility.start_date";
    private static final String END_DATE_FACT_KEY = "facility.end_date";
    private static final String FOREIGN_VALUE_FACT_KEY =
            "facility.amount.foreign_value";
    private static final String DISCLOSED_FX_RATE_FACT_KEY =
            "facility.amount.disclosed_fx_rate";
    private static final String KRW_UNIT = "KRW";
    private static final String PERCENT_UNIT = "PERCENT";
    private static final String ISO_DATE_UNIT = "ISO_DATE";
    private static final String USD_UNIT = "USD";
    private static final String KRW_PER_USD_UNIT = "KRW_PER_USD";

    // 공시비율을 신뢰할 수 없어 원문 소수 자릿수(d)를 알 수 없을 때만 쓰는
    // 표시용 기본 자릿수. 이 경우 비교는 하지 않으므로(NOT_COMPARABLE)
    // 판정에는 영향을 주지 않는다.
    private static final int DEFAULT_DISPLAY_SCALE = 1;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final String PERCENT_DISPLAY_UNIT = "%";
    private static final String DAYS_UNIT = "일";

    @Override
    public CalculationResult calculate(
            CalculationCommand command,
            List<RetrievedFact> facts
    ) {
        Objects.requireNonNull(command, "command는 필수입니다.");
        Objects.requireNonNull(facts, "facts는 필수입니다.");

        return switch (command.operation()) {
            case RATIO -> calculateRatio(command, facts);
            case DATE_DURATION -> calculateDurationDays(command, facts);
            case PRODUCT -> calculateFxCheck(command, facts);
            default -> notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    PERCENT_DISPLAY_UNIT,
                    "아직 지원하지 않는 연산입니다: " + command.operation()
            );
        };
    }

    private CalculationResult calculateRatio(
            CalculationCommand command,
            List<RetrievedFact> facts
    ) {
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
                    PERCENT_DISPLAY_UNIT,
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
                    PERCENT_DISPLAY_UNIT,
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
                    PERCENT_DISPLAY_UNIT,
                    "VERIFIED 상태가 아닌 Fact는 계산에 사용할 수 없습니다."
            );
        }

        if (!Objects.equals(amount.disclosureId(), equity.disclosureId())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    PERCENT_DISPLAY_UNIT,
                    "투자금액과 자기자본이 같은 공시의 Fact가 아닙니다."
            );
        }

        if (!KRW_UNIT.equals(amount.unit()) || !KRW_UNIT.equals(equity.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    PERCENT_DISPLAY_UNIT,
                    "투자금액과 자기자본이 모두 KRW 단위가 아닙니다."
            );
        }

        if (!samePeriod(amount, equity)) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    PERCENT_DISPLAY_UNIT,
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
                    PERCENT_DISPLAY_UNIT,
                    "정규화된 금액을 숫자로 변환할 수 없습니다."
            );
        }

        if (equityValue.signum() == 0) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    PERCENT_DISPLAY_UNIT,
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
                    PERCENT_DISPLAY_UNIT,
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
                    PERCENT_DISPLAY_UNIT,
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
                PERCENT_DISPLAY_UNIT,
                matches
                        ? "재계산값과 공시값이 공시 소수 자릿수(" + displayScale
                                + "자리) 반올림 기준으로 일치합니다."
                        : "재계산값과 공시값이 공시 소수 자릿수(" + displayScale
                                + "자리) 반올림 기준으로 다릅니다."
        );
    }

    /**
     * {@code facility.end_date − facility.start_date + 1}일을 계산한다.
     * 공시에 비교할 "총 투자기간" 값이 따로 없으므로, 계산이 성공해도
     * 항상 {@link CalculationVerdict#NOT_COMPARABLE}로 계산값만 제공한다.
     */
    private CalculationResult calculateDurationDays(
            CalculationCommand command,
            List<RetrievedFact> facts
    ) {
        Map<String, List<RetrievedFact>> byKey = indexByFactKey(facts);
        List<RetrievedFact> startCandidates =
                byKey.getOrDefault(START_DATE_FACT_KEY, List.of());
        List<RetrievedFact> endCandidates =
                byKey.getOrDefault(END_DATE_FACT_KEY, List.of());

        if (startCandidates.size() > 1 || endCandidates.size() > 1) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    DAYS_UNIT,
                    "같은 factKey의 Fact가 여러 개라 임의로 선택할 수 없습니다: "
                            + START_DATE_FACT_KEY + ", " + END_DATE_FACT_KEY
            );
        }

        RetrievedFact startFact = startCandidates.isEmpty()
                ? null : startCandidates.get(0);
        RetrievedFact endFact = endCandidates.isEmpty()
                ? null : endCandidates.get(0);

        if (startFact == null || startFact.normalizedValue() == null
                || endFact == null || endFact.normalizedValue() == null) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    DAYS_UNIT,
                    "계산에 필요한 facility.start_date 또는 "
                            + "facility.end_date가 없습니다."
            );
        }

        List<String> inputFactIds =
                List.of(startFact.factId(), endFact.factId());

        if (startFact.validationStatus() != FactValidationStatus.VERIFIED
                || endFact.validationStatus() != FactValidationStatus.VERIFIED) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "VERIFIED 상태가 아닌 Fact는 계산에 사용할 수 없습니다."
            );
        }

        if (!Objects.equals(
                startFact.disclosureId(),
                endFact.disclosureId()
        )) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "시작일과 종료일이 같은 공시의 Fact가 아닙니다."
            );
        }

        if (!ISO_DATE_UNIT.equals(startFact.unit())
                || !ISO_DATE_UNIT.equals(endFact.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "시작일과 종료일이 모두 ISO_DATE 단위가 아닙니다."
            );
        }

        if (!samePeriod(startFact, endFact)) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "시작일과 종료일의 기준시점(기간)이 다릅니다."
            );
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startFact.normalizedValue());
            end = LocalDate.parse(endFact.normalizedValue());
        } catch (DateTimeException e) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "정규화된 날짜를 변환할 수 없습니다."
            );
        }

        if (end.isBefore(start)) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    DAYS_UNIT,
                    "종료일이 시작일보다 빠릅니다."
            );
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;

        return new CalculationResult(
                command.operation(),
                inputFactIds,
                CalculationVerdict.NOT_COMPARABLE,
                (double) days,
                String.valueOf(days),
                null,
                DAYS_UNIT,
                "공시된 총 투자기간 값과 비교할 대상이 없어 계산값만 제공합니다."
        );
    }

    /**
     * {@code facility.amount.foreign_value × facility.amount.disclosed_fx_rate}
     * (원화 환산액)을 {@code facility.amount}(공시된 KRW 투자금액)와
     * 비교한다. 두 입력 모두 서술 문장에서 정규식으로 뽑아낸 값이라
     * 존재하지 않는 문서가 대부분이며, 그 경우 {@code NOT_CALCULABLE}을
     * 반환한다.
     */
    private CalculationResult calculateFxCheck(
            CalculationCommand command,
            List<RetrievedFact> facts
    ) {
        Map<String, List<RetrievedFact>> byKey = indexByFactKey(facts);
        List<RetrievedFact> foreignCandidates =
                byKey.getOrDefault(FOREIGN_VALUE_FACT_KEY, List.of());
        List<RetrievedFact> rateCandidates =
                byKey.getOrDefault(DISCLOSED_FX_RATE_FACT_KEY, List.of());
        List<RetrievedFact> amountCandidates =
                byKey.getOrDefault(AMOUNT_FACT_KEY, List.of());

        if (foreignCandidates.size() > 1 || rateCandidates.size() > 1
                || amountCandidates.size() > 1) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    KRW_UNIT,
                    "같은 factKey의 Fact가 여러 개라 임의로 선택할 수 없습니다: "
                            + FOREIGN_VALUE_FACT_KEY + ", "
                            + DISCLOSED_FX_RATE_FACT_KEY + ", "
                            + AMOUNT_FACT_KEY
            );
        }

        RetrievedFact foreign = foreignCandidates.isEmpty()
                ? null : foreignCandidates.get(0);
        RetrievedFact rate = rateCandidates.isEmpty()
                ? null : rateCandidates.get(0);
        RetrievedFact amount = amountCandidates.isEmpty()
                ? null : amountCandidates.get(0);

        if (foreign == null || foreign.normalizedValue() == null
                || rate == null || rate.normalizedValue() == null
                || amount == null || amount.normalizedValue() == null) {
            return notCalculable(
                    command.operation(),
                    List.of(),
                    null,
                    KRW_UNIT,
                    "계산에 필요한 " + FOREIGN_VALUE_FACT_KEY + ", "
                            + DISCLOSED_FX_RATE_FACT_KEY + " 또는 "
                            + AMOUNT_FACT_KEY + "가 없습니다."
            );
        }

        List<String> inputFactIds = List.of(
                foreign.factId(),
                rate.factId(),
                amount.factId()
        );

        if (foreign.validationStatus() != FactValidationStatus.VERIFIED
                || rate.validationStatus() != FactValidationStatus.VERIFIED
                || amount.validationStatus()
                        != FactValidationStatus.VERIFIED) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "VERIFIED 상태가 아닌 Fact는 계산에 사용할 수 없습니다."
            );
        }

        if (!Objects.equals(foreign.disclosureId(), amount.disclosureId())
                || !Objects.equals(
                        rate.disclosureId(),
                        amount.disclosureId()
                )) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "외화금액·환율·투자금액이 같은 공시의 Fact가 아닙니다."
            );
        }

        if (!USD_UNIT.equals(foreign.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "지원하지 않는 외화 단위입니다: " + foreign.unit()
            );
        }
        if (!KRW_PER_USD_UNIT.equals(rate.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "지원하지 않는 환율 단위입니다: " + rate.unit()
            );
        }
        if (!KRW_UNIT.equals(amount.unit())) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "투자금액이 KRW 단위가 아닙니다."
            );
        }

        BigDecimal foreignValue;
        BigDecimal rateValue;
        BigDecimal amountValue;
        try {
            foreignValue = new BigDecimal(foreign.normalizedValue());
            rateValue = new BigDecimal(rate.normalizedValue());
            amountValue = new BigDecimal(amount.normalizedValue());
        } catch (NumberFormatException e) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "정규화된 값을 숫자로 변환할 수 없습니다."
            );
        }

        if (foreignValue.signum() <= 0 || rateValue.signum() <= 0) {
            return notCalculable(
                    command.operation(),
                    inputFactIds,
                    null,
                    KRW_UNIT,
                    "외화금액 또는 환율이 0 이하입니다."
            );
        }

        BigDecimal raw = foreignValue.multiply(rateValue);
        BigDecimal display = raw.setScale(0, ROUNDING_MODE);
        boolean matches = display.compareTo(amountValue) == 0;

        return new CalculationResult(
                command.operation(),
                inputFactIds,
                matches ? CalculationVerdict.MATCH : CalculationVerdict.MISMATCH,
                raw.doubleValue(),
                display.toPlainString(),
                amountValue.toPlainString(),
                KRW_UNIT,
                matches
                        ? "외화금액×환율 환산액이 공시된 투자금액과 일치합니다."
                        : "외화금액×환율 환산액이 공시된 투자금액과 다릅니다."
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
            String unit,
            String reason
    ) {
        return new CalculationResult(
                operation,
                inputFactIds,
                CalculationVerdict.NOT_CALCULABLE,
                null,
                null,
                disclosedValue,
                unit,
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
