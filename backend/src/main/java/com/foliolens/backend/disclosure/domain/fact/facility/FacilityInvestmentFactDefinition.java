package com.foliolens.backend.disclosure.domain.fact.facility;

import com.foliolens.backend.disclosure.domain.fact.FactValueType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 신규시설투자 첫 수직 구현에서 사용하는 우선 Fact의 승인 키와
 * 원문 표 레이블 계약.
 */
public enum FacilityInvestmentFactDefinition {

    TYPE(
            "facility.type",
            FactValueType.CODE,
            null,
            false,
            Set.of("투자구분", "투자 구분"),
            Set.of()
    ),
    TARGET(
            "facility.target",
            FactValueType.TEXT,
            null,
            true,
            Set.of("투자대상", "투자 대상"),
            Set.of()
    ),
    AMOUNT(
            "facility.amount",
            FactValueType.DECIMAL,
            "KRW",
            true,
            Set.of(
                    "투자금액",
                    "투자 금액",
                    "투자예정금액",
                    "투자규모"
            ),
            Set.of()
    ),
    EQUITY_AMOUNT(
            "facility.equity_amount",
            FactValueType.DECIMAL,
            "KRW",
            true,
            Set.of("자기자본", "자기 자본"),
            Set.of()
    ),
    EQUITY_RATIO(
            "facility.equity_ratio",
            FactValueType.DECIMAL,
            "PERCENT",
            true,
            Set.of("자기자본대비", "자기자본 대비"),
            Set.of()
    ),
    PURPOSE(
            "facility.purpose",
            FactValueType.TEXT,
            null,
            true,
            Set.of("투자목적", "투자 목적"),
            Set.of()
    ),
    START_DATE(
            "facility.start_date",
            FactValueType.DATE,
            "ISO_DATE",
            true,
            Set.of("투자기간", "투자 기간", "시작일"),
            Set.of("시작일", "시작", "투자기간 시작")
    ),
    END_DATE(
            "facility.end_date",
            FactValueType.DATE,
            "ISO_DATE",
            true,
            Set.of("투자기간", "투자 기간", "종료일"),
            Set.of("종료일", "종료", "투자기간 종료")
    ),
    DECISION_DATE(
            "facility.decision_date",
            FactValueType.DATE,
            "ISO_DATE",
            true,
            Set.of(
                    "이사회결의일(결정일)",
                    "이사회결의일",
                    "이사회 결의일",
                    "결정일"
            ),
            Set.of()
    ),
    // 아래 3개는 별도 표 행이 아니라 "기타 투자판단과 관련한 중요사항"
    // 서술형 칸의 자유 문장에서 정규식으로 값을 뽑아낸다(라벨-값 표
    // 매칭이 아니므로 원문에 해당 서술이 없으면 정규화 단계에서
    // UNMAPPED로 남는다). 원문 표 라벨에 단위(원|%등)가 없어 core 8개와
    // 같은 "원문 단위 명시" 요건은 검증기에서 예외로 둔다.
    FOREIGN_VALUE(
            "facility.amount.foreign_value",
            FactValueType.DECIMAL,
            "USD",
            false,
            Set.of("기타 투자판단과 관련한 중요사항", "기타 투자판단에 참고할 사항"),
            Set.of()
    ),
    CURRENCY_CODE(
            "facility.amount.currency_code",
            FactValueType.CODE,
            null,
            false,
            Set.of("기타 투자판단과 관련한 중요사항", "기타 투자판단에 참고할 사항"),
            Set.of()
    ),
    DISCLOSED_FX_RATE(
            "facility.amount.disclosed_fx_rate",
            FactValueType.DECIMAL,
            "KRW_PER_USD",
            false,
            Set.of("기타 투자판단과 관련한 중요사항", "기타 투자판단에 참고할 사항"),
            Set.of()
    );

    private final String factKey;
    private final FactValueType valueType;
    private final String normalizedUnit;
    private final boolean core;
    private final Set<String> rowLabels;
    private final Set<String> columnLabels;

    FacilityInvestmentFactDefinition(
            String factKey,
            FactValueType valueType,
            String normalizedUnit,
            boolean core,
            Set<String> rowLabels,
            Set<String> columnLabels
    ) {
        this.factKey = factKey;
        this.valueType = valueType;
        this.normalizedUnit = normalizedUnit;
        this.core = core;
        this.rowLabels = Set.copyOf(new LinkedHashSet<>(rowLabels));
        this.columnLabels = Set.copyOf(new LinkedHashSet<>(columnLabels));
    }

    public String factKey() {
        return factKey;
    }

    public FactValueType valueType() {
        return valueType;
    }

    public String normalizedUnit() {
        return normalizedUnit;
    }

    public boolean core() {
        return core;
    }

    public Set<String> rowLabels() {
        return rowLabels;
    }

    public Set<String> columnLabels() {
        return columnLabels;
    }

    public boolean matchesRowLabel(String candidate) {
        String normalized = normalizeLabel(candidate);
        return rowLabels.stream()
                .map(FacilityInvestmentFactDefinition::normalizeLabel)
                .anyMatch(normalized::equals);
    }

    public boolean matchesColumnLabel(String candidate) {
        if (columnLabels.isEmpty()) {
            return true;
        }
        String normalized = normalizeLabel(candidate);
        return columnLabels.stream()
                .map(FacilityInvestmentFactDefinition::normalizeLabel)
                .anyMatch(normalized::equals);
    }

    public static Optional<FacilityInvestmentFactDefinition> fromFactKey(
            String factKey
    ) {
        if (factKey == null || factKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = factKey.strip();
        return Arrays.stream(values())
                .filter(definition -> definition.factKey.equals(normalized))
                .findFirst();
    }

    public static Set<FacilityInvestmentFactDefinition> coreDefinitions() {
        return Arrays.stream(values())
                .filter(FacilityInvestmentFactDefinition::core)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value
                .replace('\u00a0', ' ')
                .strip()
                .replaceFirst("^[\\-–—·ㆍ※]+\\s*", "")
                .replaceFirst("^\\d+\\s*[.)]\\s*", "")
                .replaceFirst(
                        "\\s*\\((?:원|천원|백만원|억원|%|퍼센트)\\)\\s*$",
                        ""
                )
                .replaceAll("\\s+", "");
    }
}
