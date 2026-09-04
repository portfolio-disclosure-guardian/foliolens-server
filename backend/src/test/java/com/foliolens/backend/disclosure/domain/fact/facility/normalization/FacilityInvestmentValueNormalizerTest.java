package com.foliolens.backend.disclosure.domain.fact.facility.normalization;

import com.foliolens.backend.disclosure.domain.fact.CodeFactValue;
import com.foliolens.backend.disclosure.domain.fact.DateFactValue;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.disclosure.domain.fact.TextFactValue;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityInvestmentValueNormalizerTest {

    private final FacilityInvestmentValueNormalizer normalizer =
            new FacilityInvestmentValueNormalizer();

    // ---- KRW 금액 ----

    @Test
    void 쉼표와_원_단위의_금액을_KRW로_정규화한다() {
        FactValueNormalizationResult result = normalizer.normalizeKrwAmount(
                "5,296,200,000,000",
                "원"
        );

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("5296200000000")
                ));
        assertThat(result.normalizedUnit()).isEqualTo("KRW");
        assertThat(result.availabilityStatus())
                .isEqualTo(FactAvailabilityStatus.AVAILABLE);
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MAPPED);
    }

    @Test
    void 값에_포함된_백만원_단위도_같은_KRW값으로_정규화한다() {
        FactValueNormalizationResult withComma = normalizer.normalizeKrwAmount(
                "5,296,200,000,000",
                "원"
        );
        FactValueNormalizationResult withEmbeddedUnit =
                normalizer.normalizeKrwAmount("5,296,200백만원", null);

        assertThat(withEmbeddedUnit.mapped()).isTrue();
        assertThat(withEmbeddedUnit.normalizedValue())
                .isEqualTo(withComma.normalizedValue());
        assertThat(withEmbeddedUnit.normalizedUnit()).isEqualTo("KRW");
    }

    @Test
    void 억원_단위도_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeKrwAmount("12.5억원", null);

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("1250000000.0")
                ));
    }

    @Test
    void 공백이_섞인_금액도_정규화한다() {
        FactValueNormalizationResult result = normalizer.normalizeKrwAmount(
                " 5,296,200,000,000 ",
                "원"
        );

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("5296200000000")
                ));
    }

    @Test
    void 단위를_확인할_수_없는_금액은_모호로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeKrwAmount("5296200000000", null);

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.AMBIGUOUS);
        assertThat(result.availabilityStatus())
                .isEqualTo(FactAvailabilityStatus.AMBIGUOUS);
    }

    @Test
    void 값에_포함된_단위와_별도_단위가_다르면_모호로_처리한다() {
        FactValueNormalizationResult result = normalizer.normalizeKrwAmount(
                "5,296,200백만원",
                "억원"
        );

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.AMBIGUOUS);
    }

    @Test
    void 숫자로_변환할_수_없는_금액은_UNMAPPED로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeKrwAmount("약 오조원", "원");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
        assertThat(result.availabilityStatus())
                .isEqualTo(FactAvailabilityStatus.PARSE_FAILED);
    }

    @Test
    void 빈_금액은_MISSING으로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeKrwAmount("", "원");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MISSING);
        assertThat(result.availabilityStatus())
                .isEqualTo(FactAvailabilityStatus.NOT_STATED);
    }

    // ---- 비율 ----

    @Test
    void 퍼센트_기호가_없는_비율도_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeRatio("9.90", "%");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("9.90")));
        assertThat(result.normalizedUnit()).isEqualTo("PERCENT");
    }

    @Test
    void 퍼센트_기호가_포함된_비율도_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeRatio("9.90%", null);

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("9.90")));
    }

    @Test
    void 예상하지_못한_단위의_비율은_모호로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeRatio("9.90", "원");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.AMBIGUOUS);
    }

    @Test
    void 숫자가_아닌_비율은_UNMAPPED로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeRatio("약간", "%");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
    }

    // ---- 날짜 ----

    @Test
    void ISO_날짜를_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("2024-04-24");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DateFactValue(LocalDate.of(2024, 4, 24)));
        assertThat(result.normalizedUnit()).isEqualTo("ISO_DATE");
    }

    @Test
    void 점으로_구분된_날짜를_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("2024.04.24");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DateFactValue(LocalDate.of(2024, 4, 24)));
    }

    @Test
    void 한글_날짜_표현을_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("2024년 4월 24일");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DateFactValue(LocalDate.of(2024, 4, 24)));
    }

    @Test
    void 압축된_8자리_날짜를_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("20240424");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DateFactValue(LocalDate.of(2024, 4, 24)));
    }

    @Test
    void 존재하지_않는_달력_날짜는_UNMAPPED로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("2024-13-40");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
    }

    @Test
    void 지원하지_않는_날짜_형식은_UNMAPPED로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeDate("2024년 4월");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
    }

    // ---- TEXT ----

    @Test
    void 앞뒤_공백과_연속_공백만_정리하고_의미는_유지한다() {
        FactValueNormalizationResult result = normalizer.normalizeText(
                "  청주   M15X   건설  "
        );

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new TextFactValue("청주 M15X 건설"));
    }

    @Test
    void 빈_텍스트는_MISSING으로_처리한다() {
        FactValueNormalizationResult result = normalizer.normalizeText("   ");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MISSING);
    }

    // ---- CODE(시설투자 구분) ----

    @Test
    void 승인된_시설투자_구분을_CODE로_정규화한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeFacilityType("시설증설");

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new CodeFactValue("EXPANSION"));
    }

    @Test
    void 승인되지_않은_시설투자_구분은_UNMAPPED로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeFacilityType("부지매입");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
    }

    @Test
    void 여러_구분이_동시에_매칭되면_모호로_처리한다() {
        FactValueNormalizationResult result =
                normalizer.normalizeFacilityType("증설 및 교체");

        assertThat(result.mapped()).isFalse();
        assertThat(result.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.AMBIGUOUS);
    }

    // ---- definition 기반 dispatch ----

    @Test
    void definition을_기준으로_적절한_정규화_방식을_고른다() {
        FactValueNormalizationResult amount = normalizer.normalize(
                FacilityInvestmentFactDefinition.AMOUNT,
                new DisclosureEvidenceValue(
                        "투자금액 | 5,296,200,000,000",
                        "투자금액",
                        null,
                        "5,296,200,000,000",
                        "원",
                        null
                )
        );
        FactValueNormalizationResult ratio = normalizer.normalize(
                FacilityInvestmentFactDefinition.EQUITY_RATIO,
                new DisclosureEvidenceValue(
                        "자기자본대비 | 9.90",
                        "자기자본대비",
                        null,
                        "9.90",
                        "%",
                        null
                )
        );

        assertThat(amount.valueType()).isEqualTo(FactValueType.DECIMAL);
        assertThat(amount.normalizedUnit()).isEqualTo("KRW");
        assertThat(ratio.normalizedUnit()).isEqualTo("PERCENT");
    }
}
