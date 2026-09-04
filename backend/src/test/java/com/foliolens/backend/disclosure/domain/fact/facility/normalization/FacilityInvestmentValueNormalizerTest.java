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

    // ---- 외화금액·환율(FX) — "기타 투자판단과 관련한 중요사항"
    // 서술 문장에서 정규식으로 뽑아낸다. 아래 문장은 실제 접수번호
    // 20230214800345, 20231026800363, 20240926800370의 원문이다. ----

    @Test
    void 선가_문장에서_외화_투자금액을_뽑아낸다() {
        FactValueNormalizationResult result = normalizer.normalizeForeignValue(
                "- 상기 투자금액은 총 선가 USD 1,118,534,000에 이사회결의일 "
                        + "2영업일 전 최초고시환율 1,263.1KRW/USD를 적용한 "
                        + "금액입니다."
        );

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("1118534000")));
        assertThat(result.normalizedUnit()).isEqualTo("USD");
    }

    @Test
    void 매매기준율_문장에서_외화_투자금액과_환율을_뽑아낸다() {
        String notes = "- 상기 \"2. 투자내역 - 투자금액\"은 USD 1,850,197,160에 "
                + "이사회결의일인 2023년 10월 26일 서울외국환중개 최초고시 "
                + "매매기준율(1,347.00 KRW/USD)을 적용하여 원화환산한 "
                + "금액입니다.";

        FactValueNormalizationResult foreignValue =
                normalizer.normalizeForeignValue(notes);
        FactValueNormalizationResult rate =
                normalizer.normalizeDisclosedFxRate(notes);
        FactValueNormalizationResult currency =
                normalizer.normalizeCurrencyCode(notes);

        assertThat(foreignValue.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("1850197160")
                ));
        assertThat(rate.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("1347.00")));
        assertThat(rate.normalizedUnit()).isEqualTo("KRW_PER_USD");
        assertThat(currency.normalizedValue())
                .isEqualTo(new CodeFactValue("USD"));
    }

    @Test
    void 환율_숫자_없이_통화만_언급되면_환율은_UNMAPPED로_남긴다() {
        // 20240926800370: 외화 원금(USD 93,846,633)은 있지만 환율은
        // 숫자 없이 "USD환율을 적용한 금액임"으로만 서술됨.
        String notes = "- 상기 '2. 투자내역 - 투자금액'은 총 투자금액은 "
                + "USD 93,846,633이며 이사회 전일 USD환율을 적용한 금액임";

        FactValueNormalizationResult foreignValue =
                normalizer.normalizeForeignValue(notes);
        FactValueNormalizationResult rate =
                normalizer.normalizeDisclosedFxRate(notes);

        assertThat(foreignValue.mapped()).isTrue();
        assertThat(foreignValue.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("93846633")));
        assertThat(rate.mapped()).isFalse();
        assertThat(rate.normalizationStatus())
                .isEqualTo(FactNormalizationStatus.UNMAPPED);
    }

    @Test
    void 외화_서술이_없으면_모두_UNMAPPED로_남긴다() {
        String notes = "- 상기 \"2.투자내역\"의 투자금액은 승인한도금액이며, "
                + "물가 및 환율변동 등을 감안하여 예상금액의 130% 수준으로 "
                + "설정하였습니다.";

        assertThat(normalizer.normalizeForeignValue(notes).mapped()).isFalse();
        assertThat(normalizer.normalizeDisclosedFxRate(notes).mapped())
                .isFalse();
        assertThat(normalizer.normalizeCurrencyCode(notes).mapped()).isFalse();
    }

    @Test
    void 이_공시_투자금액과_무관한_문장의_USD_금액은_가져오지_않는다() {
        // 실제 접수번호 20240612800420: 해외 계열회사(Hyosung HICO)의
        // 별도 투자를 언급한 문장에 USD 금액이 있지만, 그 항목에는
        // "투자금액"이라는 표현이 없다. 이 공시 자신의 투자금액에 대한
        // 서술이 아니므로 오귀속하지 않아야 한다.
        String notes = "- 본 투자건 이외에 당사의 해외 계열회사인 "
                + "Hyosung HICO, Ltd.에서 USD 49백만(약669억원)\n"
                + "의 멤피스공장 증설 투자를 진행할 예정임.";

        assertThat(normalizer.normalizeForeignValue(notes).mapped()).isFalse();
        assertThat(normalizer.normalizeDisclosedFxRate(notes).mapped())
                .isFalse();
        assertThat(normalizer.normalizeCurrencyCode(notes).mapped()).isFalse();
    }

    @Test
    void 투자금액_항목만_골라_외화_금액을_찾는다() {
        // 같은 노트 칸 안에 무관한 USD 문장과 실제 투자금액 문장이
        // 함께 있어도, "투자금액"을 언급한 항목에서만 값을 가져온다.
        String notes = "- 본 투자건 이외에 당사의 해외 계열회사에서 "
                + "USD 49백만의 별도 투자를 진행할 예정임.\n"
                + "- 상기 투자금액은 총 선가 USD 1,118,534,000에 "
                + "이사회결의일 2영업일 전 최초고시환율 1,263.1KRW/USD를 "
                + "적용한 금액입니다.";

        FactValueNormalizationResult result =
                normalizer.normalizeForeignValue(notes);

        assertThat(result.mapped()).isTrue();
        assertThat(result.normalizedValue())
                .isEqualTo(new DecimalFactValue(new BigDecimal("1118534000")));
    }

    @Test
    void 외화금액이_빈값이면_MISSING으로_처리한다() {
        assertThat(normalizer.normalizeForeignValue("   ").normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MISSING);
        assertThat(normalizer.normalizeDisclosedFxRate(null)
                .normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MISSING);
        assertThat(normalizer.normalizeCurrencyCode("").normalizationStatus())
                .isEqualTo(FactNormalizationStatus.MISSING);
    }
}
