package com.foliolens.backend.disclosure.domain.fact.facility.normalization;

import com.foliolens.backend.disclosure.domain.fact.CodeFactValue;
import com.foliolens.backend.disclosure.domain.fact.DateFactValue;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.disclosure.domain.fact.TextFactValue;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시설투자 Evidence의 원문값·원문단위를 승인된 표준값으로 정규화한다.
 *
 * 실패·모호·누락을 0이나 임의 값으로 대체하지 않고, 성공(MAPPED)하지
 * 못한 모든 경우를 {@link FactValueNormalizationResult}의 명시적 상태로
 * 반환한다.
 */
public class FacilityInvestmentValueNormalizer {

    // KRW: FacilityInvestmentFactDefinition의 단위 접미사 규칙(원|천원|백만원|억원)과 맞춘다.
    // 긴 접미사부터 확인해야 "백만원"이 "원"으로 잘못 잘리지 않는다.
    private static final List<String> KRW_SUFFIXES_LONGEST_FIRST =
            List.of("백만원", "억원", "천원", "원");
    private static final Map<String, BigDecimal> KRW_SUFFIX_MULTIPLIERS =
            Map.of(
                    "원", BigDecimal.ONE,
                    "천원", new BigDecimal("1000"),
                    "백만원", new BigDecimal("1000000"),
                    "억원", new BigDecimal("100000000")
            );

    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private static final Pattern ISO_DATE =
            Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern DOT_DATE =
            Pattern.compile("^(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})$");
    private static final Pattern KOREAN_DATE =
            Pattern.compile("^(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일$");
    private static final Pattern COMPACT_DATE =
            Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final List<Pattern> DATE_PATTERNS = List.of(
            ISO_DATE, DOT_DATE, KOREAN_DATE, COMPACT_DATE
    );

    // 승인된 시설투자 구분만 정규화한다. 매칭되지 않는 표현은 추측하지 않고
    // UNMAPPED로 반환한다.
    private static final Map<String, String> FACILITY_TYPE_KEYWORDS =
            new LinkedHashMap<>();

    // "기타 투자판단과 관련한 중요사항" 서술형 문장에서 외화금액·환율을
    // 뽑아낸다. 43건 실제 코퍼스에서 확인된 표현은 전부 USD이므로
    // 지금은 USD만 승인한다. 다른 통화 표현은 추측하지 않고 UNMAPPED로
    // 남긴다.
    private static final Pattern FX_FOREIGN_VALUE_PATTERN =
            Pattern.compile("USD\\s*([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern FX_RATE_PATTERN =
            Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*KRW\\s*/\\s*USD");
    // 노트 칸에는 이 공시의 투자금액과 무관한 문장(예: 해외 계열회사의
    // 별도 투자)도 USD 금액을 언급할 수 있다. "- "로 시작하는 항목
    // 단위로 나눠 "투자금액"이 함께 언급된 항목에서만 외화·환율을
    // 찾아 오귀속을 막는다.
    private static final Pattern NOTE_BULLET_SPLIT_PATTERN =
            Pattern.compile("(?:^|\\r?\\n)\\s*-\\s*");
    private static final String INVESTMENT_AMOUNT_KEYWORD = "투자금액";

    static {
        FACILITY_TYPE_KEYWORDS.put("신설", "NEW_CONSTRUCTION");
        FACILITY_TYPE_KEYWORDS.put("증설", "EXPANSION");
        FACILITY_TYPE_KEYWORDS.put("개보수", "RENOVATION");
        FACILITY_TYPE_KEYWORDS.put("교체", "REPLACEMENT");
        FACILITY_TYPE_KEYWORDS.put("이전", "RELOCATION");
        FACILITY_TYPE_KEYWORDS.put("기타", "OTHER");
    }

    public FactValueNormalizationResult normalize(
            FacilityInvestmentFactDefinition definition,
            DisclosureEvidenceValue value
    ) {
        Objects.requireNonNull(definition, "definition은 필수입니다.");
        Objects.requireNonNull(value, "value는 필수입니다.");

        // 아래 3개는 "기타 투자판단과 관련한 중요사항" 서술형 칸의 자유
        // 문장에서 정규식으로 값을 뽑아내므로, valueType 기준 공통 분기가
        // 아니라 definition별로 먼저 처리한다.
        if (definition == FacilityInvestmentFactDefinition.FOREIGN_VALUE) {
            return normalizeForeignValue(value.rawValue());
        }
        if (definition == FacilityInvestmentFactDefinition.CURRENCY_CODE) {
            return normalizeCurrencyCode(value.rawValue());
        }
        if (definition
                == FacilityInvestmentFactDefinition.DISCLOSED_FX_RATE) {
            return normalizeDisclosedFxRate(value.rawValue());
        }
        // 정정공시 "정정사항" 비교표의 정정전/정정후 값 셀은 별도 단위
        // 열이 없다("2. 투자금액"처럼 라벨에 "(원)"조차 없을 때가
        // 많다). 이 두 Fact는 항상 원 단위 금액이라는 것을 표 구조
        // 자체가 보장하므로, rawUnit을 "원"으로 명시해 정규화한다.
        if (definition
                == FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_BEFORE
                || definition
                        == FacilityInvestmentFactDefinition
                                .AMOUNT_CORRECTION_AFTER) {
            return normalizeKrwAmount(value.rawValue(), "원");
        }

        return switch (definition.valueType()) {
            case DECIMAL -> "PERCENT".equals(definition.normalizedUnit())
                    ? normalizeRatio(value.rawValue(), value.rawUnit())
                    : normalizeKrwAmount(value.rawValue(), value.rawUnit());
            case TEXT -> normalizeText(value.rawValue());
            case DATE -> normalizeDate(value.rawValue());
            case CODE -> normalizeFacilityType(value.rawValue());
            default -> FactValueNormalizationResult.unmapped(
                    definition.valueType(),
                    "지원하지 않는 valueType입니다: " + definition.valueType()
            );
        };
    }

    /**
     * "5,296,200,000,000"(rawUnit="원") 또는 "5,296,200백만원"(단위가 값에
     * 포함된 경우)처럼 쉼표·공백이 섞인 KRW 금액을 정규화한다.
     */
    public FactValueNormalizationResult normalizeKrwAmount(
            String rawValue,
            String rawUnit
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.DECIMAL);
        }

        String cleaned = stripWhitespace(rawValue);

        String embeddedSuffix = null;
        String numericPart = cleaned;
        for (String suffix : KRW_SUFFIXES_LONGEST_FIRST) {
            if (cleaned.endsWith(suffix)) {
                embeddedSuffix = suffix;
                numericPart = cleaned.substring(
                        0,
                        cleaned.length() - suffix.length()
                );
                break;
            }
        }

        String unitText = normalizeUnitText(rawUnit);
        String effectiveSuffix;
        if (embeddedSuffix != null && unitText != null) {
            if (!embeddedSuffix.equals(unitText)) {
                return FactValueNormalizationResult.ambiguous(
                        FactValueType.DECIMAL,
                        "원문값의 단위(" + embeddedSuffix
                                + ")와 별도 단위(" + unitText + ")가 다릅니다."
                );
            }
            effectiveSuffix = embeddedSuffix;
        } else if (embeddedSuffix != null) {
            effectiveSuffix = embeddedSuffix;
        } else if (unitText != null) {
            if (!KRW_SUFFIX_MULTIPLIERS.containsKey(unitText)) {
                return FactValueNormalizationResult.unmapped(
                        FactValueType.DECIMAL,
                        "지원하지 않는 금액 단위입니다: " + unitText
                );
            }
            effectiveSuffix = unitText;
        } else {
            return FactValueNormalizationResult.ambiguous(
                    FactValueType.DECIMAL,
                    "금액 단위를 확인할 수 없습니다."
            );
        }

        numericPart = numericPart.replace(",", "");
        if (!NUMERIC_PATTERN.matcher(numericPart).matches()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "금액 형식을 숫자로 변환할 수 없습니다: " + rawValue
            );
        }

        BigDecimal numeric = new BigDecimal(numericPart);
        BigDecimal krw = numeric.multiply(
                KRW_SUFFIX_MULTIPLIERS.get(effectiveSuffix)
        );
        return FactValueNormalizationResult.mapped(
                new DecimalFactValue(krw),
                "KRW"
        );
    }

    /**
     * "9.90" 또는 "9.90%"처럼 표시된 비율을 BigDecimal과 PERCENT 단위로
     * 정규화한다.
     */
    public FactValueNormalizationResult normalizeRatio(
            String rawValue,
            String rawUnit
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.DECIMAL);
        }

        String cleaned = stripWhitespace(rawValue).replace(",", "");
        String numericPart = cleaned.endsWith("%")
                ? cleaned.substring(0, cleaned.length() - 1)
                : cleaned;

        String unitText = normalizeUnitText(rawUnit);
        if (unitText != null
                && !"%".equals(unitText)
                && !"퍼센트".equals(unitText)) {
            return FactValueNormalizationResult.ambiguous(
                    FactValueType.DECIMAL,
                    "비율 Fact에 예상하지 못한 단위입니다: " + unitText
            );
        }

        if (!NUMERIC_PATTERN.matcher(numericPart).matches()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "비율 형식을 숫자로 변환할 수 없습니다: " + rawValue
            );
        }

        return FactValueNormalizationResult.mapped(
                new DecimalFactValue(new BigDecimal(numericPart)),
                "PERCENT"
        );
    }

    /**
     * ISO 날짜와 DART에서 흔히 쓰는 대표 날짜 표현(yyyy.MM.dd, yyyy년 MM월
     * dd일, yyyyMMdd)을 LocalDate로 정규화한다.
     */
    public FactValueNormalizationResult normalizeDate(String rawValue) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.DATE);
        }

        String cleaned = rawValue.strip();
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(cleaned);
            if (!matcher.matches()) {
                continue;
            }
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return FactValueNormalizationResult.mapped(
                        new DateFactValue(LocalDate.of(year, month, day)),
                        "ISO_DATE"
                );
            } catch (DateTimeException e) {
                return FactValueNormalizationResult.unmapped(
                        FactValueType.DATE,
                        "유효하지 않은 날짜입니다: " + rawValue
                );
            }
        }
        return FactValueNormalizationResult.unmapped(
                FactValueType.DATE,
                "지원하지 않는 날짜 형식입니다: " + rawValue
        );
    }

    /**
     * 앞뒤 공백과 불필요한 연속 공백만 정리하고 원문의 의미는 바꾸지 않는다.
     */
    public FactValueNormalizationResult normalizeText(String rawValue) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.TEXT);
        }

        String collapsed = rawValue.strip()
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .strip();
        if (collapsed.isBlank()) {
            return FactValueNormalizationResult.missing(FactValueType.TEXT);
        }

        return FactValueNormalizationResult.mapped(
                new TextFactValue(collapsed),
                null
        );
    }

    /**
     * 승인된 시설투자 구분(신설·증설·개보수·교체·이전·기타)만 CODE로
     * 정규화한다. 매칭되지 않거나 여러 후보가 겹치면 추측하지 않는다.
     */
    public FactValueNormalizationResult normalizeFacilityType(
            String rawValue
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.CODE);
        }

        String cleaned = rawValue.strip();
        Set<String> matchedCodes = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry
                : FACILITY_TYPE_KEYWORDS.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                matchedCodes.add(entry.getValue());
            }
        }

        if (matchedCodes.isEmpty()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.CODE,
                    "승인된 시설투자 구분이 아닙니다: " + rawValue
            );
        }
        if (matchedCodes.size() > 1) {
            return FactValueNormalizationResult.ambiguous(
                    FactValueType.CODE,
                    "여러 시설투자 구분 후보가 있습니다: " + matchedCodes
            );
        }

        return FactValueNormalizationResult.mapped(
                new CodeFactValue(matchedCodes.iterator().next()),
                null
        );
    }

    /**
     * "총 선가 USD 1,118,534,000에 ... 최초고시환율 1,263.1KRW/USD를
     * 적용한 금액입니다"류 서술 문장에서 외화 원금(USD)만 뽑아낸다.
     * "USD환율"처럼 금액 없이 통화만 언급된 경우는 값이 없는 것과
     * 같으므로 UNMAPPED로 남긴다.
     */
    public FactValueNormalizationResult normalizeForeignValue(
            String rawValue
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.DECIMAL);
        }

        String scoped = investmentAmountBullets(rawValue);
        if (scoped == null) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "투자금액을 언급한 서술을 찾지 못했습니다."
            );
        }

        Matcher matcher = FX_FOREIGN_VALUE_PATTERN.matcher(scoped);
        if (!matcher.find()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "외화 투자금액(USD)을 찾지 못했습니다."
            );
        }

        String numeric = matcher.group(1).replace(",", "");
        try {
            return FactValueNormalizationResult.mapped(
                    new DecimalFactValue(new BigDecimal(numeric)),
                    "USD"
            );
        } catch (NumberFormatException e) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "외화 투자금액을 숫자로 변환할 수 없습니다: " + numeric
            );
        }
    }

    /**
     * 같은 서술 문장에서 통화코드를 뽑아낸다. 43건 실제 코퍼스에는 USD
     * 표현만 있으므로 지금은 USD만 승인한다.
     */
    public FactValueNormalizationResult normalizeCurrencyCode(
            String rawValue
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.CODE);
        }

        String scoped = investmentAmountBullets(rawValue);
        if (scoped == null
                || !FX_FOREIGN_VALUE_PATTERN.matcher(scoped).find()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.CODE,
                    "승인된 통화 표기(USD)를 찾지 못했습니다."
            );
        }
        return FactValueNormalizationResult.mapped(
                new CodeFactValue("USD"),
                null
        );
    }

    /**
     * 같은 서술 문장에서 "1,263.1KRW/USD" 또는 "(1,427.70 KRW/USD)"
     * 형태의 공시 적용환율을 뽑아낸다. 환율 자체가 문장에 없으면(예:
     * "USD환율을 적용한 금액임"처럼 숫자 없이 통화만 언급) 값이 없는
     * 것과 같으므로 추측하지 않고 UNMAPPED로 남긴다.
     */
    public FactValueNormalizationResult normalizeDisclosedFxRate(
            String rawValue
    ) {
        if (isBlank(rawValue)) {
            return FactValueNormalizationResult.missing(FactValueType.DECIMAL);
        }

        String scoped = investmentAmountBullets(rawValue);
        if (scoped == null) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "투자금액을 언급한 서술을 찾지 못했습니다."
            );
        }

        Matcher matcher = FX_RATE_PATTERN.matcher(scoped);
        if (!matcher.find()) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "공시 적용환율(KRW/USD)을 찾지 못했습니다."
            );
        }

        String numeric = matcher.group(1).replace(",", "");
        try {
            return FactValueNormalizationResult.mapped(
                    new DecimalFactValue(new BigDecimal(numeric)),
                    "KRW_PER_USD"
            );
        } catch (NumberFormatException e) {
            return FactValueNormalizationResult.unmapped(
                    FactValueType.DECIMAL,
                    "공시 적용환율을 숫자로 변환할 수 없습니다: " + numeric
            );
        }
    }

    /**
     * 노트 칸 전체 서술에서 "투자금액"을 언급한 "- " 항목만 골라
     * 이어 붙인다. 이 공시의 투자금액과 무관한 항목(예: 해외
     * 계열회사의 별도 투자 언급)에서 외화·환율을 잘못 가져오지 않기
     * 위한 범위 제한이다. 해당하는 항목이 하나도 없으면 null을
     * 반환한다.
     */
    private String investmentAmountBullets(String rawValue) {
        String[] bullets = NOTE_BULLET_SPLIT_PATTERN.split(rawValue);
        StringBuilder relevant = new StringBuilder();
        for (String bullet : bullets) {
            if (bullet.contains(INVESTMENT_AMOUNT_KEYWORD)) {
                relevant.append(bullet).append('\n');
            }
        }
        return relevant.isEmpty() ? null : relevant.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripWhitespace(String value) {
        return value.replace(' ', ' ').replaceAll("\\s+", "");
    }

    private static String normalizeUnitText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.replace(' ', ' ').strip();
        return stripped.isBlank() ? null : stripped;
    }
}
