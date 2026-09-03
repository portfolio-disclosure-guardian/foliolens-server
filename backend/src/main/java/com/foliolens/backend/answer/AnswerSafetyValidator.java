package com.foliolens.backend.answer;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedFact;

@Component
public class AnswerSafetyValidator {

    // ponytail: 문자열 답변에서는 단위가 붙은 값만 안전하게 식별한다.
    // 구조화 AnswerCandidate 계약이 생기면 정규식 대신 필드별 검증으로 교체한다.
    private static final Pattern MONEY = Pattern.compile(
            "(?<![\\d.,])((?:\\d[\\d,]*(?:\\.\\d+)?\\s*(?:조|억|만)\\s*)+"
                    + "(?:\\d[\\d,]*(?:\\.\\d+)?\\s*)?원|\\d[\\d,]*(?:\\.\\d+)?\\s*원)");
    private static final Pattern MONEY_PART = Pattern.compile(
            "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(조|억|만)?");
    private static final Pattern RATIO = Pattern.compile(
            "(?<![\\d.,])(\\d+(?:\\.\\d+)?)\\s*(?:%|퍼센트)");
    private static final Pattern KOREAN_DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern KOREAN_MONTH = Pattern.compile(
            "(?<!\\d)(\\d{4})년\\s*(\\d{1,2})월");

    public void validate(
            String renderedAnswer,
            AnswerPolicy policy,
            GoldenCase goldenCase,
            RetrievalResult retrieval,
            CalculationResult calculation) {
        if (renderedAnswer == null || renderedAnswer.isBlank()) {
            throw new BusinessException(ErrorCode.AGENT_502_1, "검증할 답변이 비어 있습니다.");
        }

        Stream.concat(policy.forbiddenExpressions().stream(), goldenCase.criticalErrors().stream())
                .filter(expression -> expression != null && !expression.isBlank())
                .filter(renderedAnswer::contains)
                .findFirst()
                .ifPresent(matched -> {
                    throw new BusinessException(
                            ErrorCode.AGENT_502_1,
                            "답변에 금지된 표현이 포함되어 있습니다: " + matched);
                });

        validateMoney(renderedAnswer, retrieval.facts());
        validateDates(renderedAnswer, retrieval);
        validateRatios(renderedAnswer, policy, retrieval.facts(), calculation);
    }

    private void validateMoney(String answer, List<RetrievedFact> facts) {
        Set<BigDecimal> allowed = facts.stream()
                .filter(AnswerSafetyValidator::isVerifiedDecimal)
                .filter(fact -> "KRW".equals(fact.unit()))
                .map(fact -> decimal(fact.normalizedValue(), "검증 fact의 금액 형식이 잘못되었습니다."))
                .collect(java.util.stream.Collectors.toSet());
        Matcher matcher = MONEY.matcher(answer);
        while (matcher.find()) {
            BigDecimal answerValue = parseKoreanMoney(matcher.group(1));
            require(allowed.stream().anyMatch(value -> value.compareTo(answerValue) == 0),
                    "답변의 금액이 검증 fact와 일치하지 않습니다.");
        }
    }

    private void validateDates(String answer, RetrievalResult retrieval) {
        Set<LocalDate> allowed = new HashSet<>();
        retrieval.facts().stream()
                .filter(fact -> fact.validationStatus() == FactValidationStatus.VERIFIED)
                .filter(fact -> fact.valueType() == FactValueType.DATE)
                .map(fact -> date(fact.normalizedValue(), "검증 fact의 날짜 형식이 잘못되었습니다."))
                .forEach(allowed::add);
        retrieval.documents().stream()
                .map(document -> document.submittedAt())
                .filter(Objects::nonNull)
                .forEach(allowed::add);

        validateFullDates(answer, KOREAN_DATE, allowed);
        validateFullDates(answer, NUMERIC_DATE, allowed);

        Set<YearMonth> allowedMonths = allowed.stream()
                .map(YearMonth::from)
                .collect(java.util.stream.Collectors.toSet());
        Matcher monthMatcher = KOREAN_MONTH.matcher(answer);
        while (monthMatcher.find()) {
            YearMonth value = yearMonth(monthMatcher.group(1), monthMatcher.group(2));
            require(allowedMonths.contains(value), "답변의 날짜가 검증 fact와 일치하지 않습니다.");
        }
    }

    private void validateRatios(
            String answer,
            AnswerPolicy policy,
            List<RetrievedFact> facts,
            CalculationResult calculation) {
        Set<BigDecimal> allowed = facts.stream()
                .filter(AnswerSafetyValidator::isVerifiedDecimal)
                .filter(fact -> "%".equals(fact.unit()))
                .map(fact -> decimal(fact.normalizedValue(), "검증 fact의 비율 형식이 잘못되었습니다."))
                .collect(java.util.stream.Collectors.toSet());
        addDecimal(allowed, calculation.displayValue());
        addDecimal(allowed, calculation.disclosedValue());
        BigDecimal raw = calculation.rawResult() == null
                ? null
                : BigDecimal.valueOf(calculation.rawResult());

        Matcher matcher = RATIO.matcher(answer);
        while (matcher.find()) {
            BigDecimal value = decimal(matcher.group(1), "답변의 비율 형식이 잘못되었습니다.");
            boolean exactMatch = allowed.stream().anyMatch(candidate -> candidate.compareTo(value) == 0);
            boolean roundedRawMatch = raw != null
                    && raw.setScale(value.scale(), policy.calculation().roundingMode()).compareTo(value) == 0;
            require(exactMatch || roundedRawMatch, "답변의 비율이 검증 fact 또는 계산 결과와 일치하지 않습니다.");
        }
    }

    private static boolean isVerifiedDecimal(RetrievedFact fact) {
        return fact.validationStatus() == FactValidationStatus.VERIFIED
                && fact.valueType() == FactValueType.DECIMAL;
    }

    private static void validateFullDates(String answer, Pattern pattern, Set<LocalDate> allowed) {
        Matcher matcher = pattern.matcher(answer);
        while (matcher.find()) {
            LocalDate value = localDate(matcher.group(1), matcher.group(2), matcher.group(3));
            require(allowed.contains(value), "답변의 날짜가 검증 fact와 일치하지 않습니다.");
        }
    }

    private static BigDecimal parseKoreanMoney(String value) {
        BigDecimal result = BigDecimal.ZERO;
        Matcher matcher = MONEY_PART.matcher(value.substring(0, value.lastIndexOf('원')));
        while (matcher.find()) {
            BigDecimal part = decimal(matcher.group(1).replace(",", ""), "답변의 금액 형식이 잘못되었습니다.");
            BigDecimal multiplier = switch (matcher.group(2) == null ? "" : matcher.group(2)) {
                case "조" -> BigDecimal.valueOf(1_000_000_000_000L);
                case "억" -> BigDecimal.valueOf(100_000_000L);
                case "만" -> BigDecimal.valueOf(10_000L);
                default -> BigDecimal.ONE;
            };
            result = result.add(part.multiply(multiplier));
        }
        return result;
    }

    private static void addDecimal(Set<BigDecimal> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(decimal(value, "계산 결과의 비율 형식이 잘못되었습니다."));
        }
    }

    private static BigDecimal decimal(String value, String message) {
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw invalid(message);
        }
    }

    private static LocalDate date(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw invalid(message);
        }
    }

    private static LocalDate localDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException exception) {
            throw invalid("답변의 날짜 형식이 잘못되었습니다.");
        }
    }

    private static YearMonth yearMonth(String year, String month) {
        try {
            return YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
        } catch (DateTimeException | NumberFormatException exception) {
            throw invalid("답변의 날짜 형식이 잘못되었습니다.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AGENT_502_1, message);
    }
}
