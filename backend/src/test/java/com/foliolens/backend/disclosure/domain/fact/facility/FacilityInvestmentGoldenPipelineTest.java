package com.foliolens.backend.disclosure.domain.fact.facility;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.facility.DeterministicDisclosureCalculator;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.facility.generation.FacilityInvestmentFactGenerator;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FacilityInvestmentValueNormalizer;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerificationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerifier;
import com.foliolens.backend.disclosure.infrastructure.chunking.TableLogicalGridBuilder;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractor;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentExtractionContext;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievedFact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SK하이닉스 골든 표(20240424800596)를 추출 → 검증 → Fact 생성 → RATIO
 * 계산까지 메모리 안에서 연결해 실행하는 수직 파이프라인 테스트.
 *
 * 실제 PostgreSQL에 적재·파싱된 데이터를 사용하는
 * {@code FacilityInvestmentEvidenceActualDatabaseTest}(
 * {@code FOLIOLENS_ACTUAL_DB_AUDIT=true}일 때만 실행)를 대체하지 않는다.
 * 이 테스트는 같은 골든 표 fixture로 정규화·검증·Fact 생성·계산까지 이어지는
 * 경로에 회귀가 없는지만 확인한다.
 */
class FacilityInvestmentGoldenPipelineTest {

    private static final String RECEIPT_NO = "20240424800596";

    private final FacilityInvestmentEvidenceExtractor extractor =
            new FacilityInvestmentEvidenceExtractor(
                    new TableLogicalGridBuilder()
            );
    private final FacilityInvestmentEvidenceVerifier verifier =
            new FacilityInvestmentEvidenceVerifier(
                    new FacilityInvestmentValueNormalizer()
            );
    private final FacilityInvestmentFactGenerator generator =
            new FacilityInvestmentFactGenerator();
    private final DeterministicDisclosureCalculator calculator =
            new DeterministicDisclosureCalculator();

    @Test
    void 추출부터_검증_Fact생성_RATIO계산까지_연결한다() {
        UUID disclosureId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );
        UUID disclosureDocumentId = UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
        );

        FacilityInvestmentEvidenceExtractionResult candidates =
                extractor.extract(context(disclosureId, disclosureDocumentId), goldenFacilityTable());
        assertThat(candidates.candidateCount()).isEqualTo(9);

        FacilityInvestmentEvidenceVerificationResult verification =
                verifier.verify(candidates);
        assertThat(verification.hasAllCoreVerified()).isTrue();

        FacilityInvestmentFactSet factSet = generator.generate(
                disclosureId,
                disclosureDocumentId,
                RECEIPT_NO,
                verification
        );
        assertThat(factSet.hasAllCoreFacts()).isTrue();

        DisclosureFact amount = factSet
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow();
        DisclosureFact equity = factSet
                .find(FacilityInvestmentFactDefinition.EQUITY_AMOUNT)
                .orElseThrow();
        DisclosureFact ratio = factSet
                .find(FacilityInvestmentFactDefinition.EQUITY_RATIO)
                .orElseThrow();

        CalculationResult result = calculator.calculate(
                new CalculationCommand(
                        CalculationOperation.RATIO,
                        new ComparisonBasis(true, false, true, true)
                ),
                List.of(
                        retrievedFact(amount),
                        retrievedFact(equity),
                        retrievedFact(ratio)
                )
        );

        assertThat(result.verdict()).isEqualTo(CalculationVerdict.MATCH);
        assertThat(result.displayValue()).isEqualTo("9.90");
        assertThat(result.disclosedValue()).isEqualTo("9.90");
    }

    private RetrievedFact retrievedFact(DisclosureFact fact) {
        return new RetrievedFact(
                fact.factId().toString(),
                fact.disclosureId().toString(),
                fact.factKey(),
                fact.valueType(),
                fact.rawValue(),
                fact.normalizedValue() == null
                        ? null
                        : switch (fact.valueType()) {
                    case DECIMAL -> ((com.foliolens.backend.disclosure.domain.fact.DecimalFactValue)
                            fact.normalizedValue()).value().toPlainString();
                    default -> fact.rawValue();
                },
                fact.normalizedUnit(),
                fact.periodStart(),
                fact.periodEnd(),
                fact.evidenceIds().stream().map(UUID::toString).toList(),
                fact.validationStatus()
        );
    }

    private FacilityInvestmentExtractionContext context(
            UUID disclosureId,
            UUID disclosureDocumentId
    ) {
        return new FacilityInvestmentExtractionContext(
                disclosureId,
                disclosureDocumentId,
                RECEIPT_NO,
                "신규시설투자등",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                null,
                "문서 서두",
                UUID.fromString("33333333-3333-3333-3333-333333333333")
        );
    }

    private ParsedDisclosureTable goldenFacilityTable() {
        return table(
                row(0,
                        cell(0, 1, 2, "1. 투자구분"),
                        cell(1, 1, 2, "시설증설")),
                row(1,
                        cell(0, 1, 2, "- 투자대상"),
                        cell(1, 1, 2, "청주 M15X 건설")),
                row(2,
                        cell(0, 4, 1, "2. 투자내역"),
                        cell(1, 1, 1, "투자금액(원)"),
                        cell(2, 1, 2, "5,296,200,000,000")),
                row(3,
                        cell(0, 1, 1, "자기자본(원)"),
                        cell(1, 1, 2, "53,503,752,397,611")),
                row(4,
                        cell(0, 1, 1, "자기자본대비(%)"),
                        cell(1, 1, 2, "9.90")),
                row(5,
                        cell(0, 1, 1, "대규모법인여부"),
                        cell(1, 1, 2, "해당")),
                row(6,
                        cell(0, 1, 2, "3. 투자목적"),
                        cell(1, 1, 2,
                                "선제적인 반도체 수요 대응을 위한 "
                                        + "차세대 DRAM 생산능력 확장")),
                row(7,
                        cell(0, 2, 1, "4. 투자기간"),
                        cell(1, 1, 1, "시작일"),
                        cell(2, 1, 2, "2024-04-24")),
                row(8,
                        cell(0, 1, 1, "종료일"),
                        cell(1, 1, 2, "2026-10-30")),
                row(9,
                        cell(0, 1, 2, "5. 이사회결의일(결정일)"),
                        cell(1, 1, 2, "2024-04-24"))
        );
    }

    private ParsedDisclosureTable table(ParsedDisclosureTableRow... rows) {
        return new ParsedDisclosureTable(1, 1, 100, List.of(rows));
    }

    private ParsedDisclosureTableRow row(
            int rowIndex,
            ParsedDisclosureTableCell... cells
    ) {
        int line = 10 + rowIndex;
        return new ParsedDisclosureTableRow(rowIndex, line, line, List.of(cells));
    }

    private ParsedDisclosureTableCell cell(
            int cellIndex,
            int rowSpan,
            int colSpan,
            String text
    ) {
        return new ParsedDisclosureTableCell(
                cellIndex,
                ParsedDisclosureTableCellType.DATA,
                rowSpan,
                colSpan,
                text,
                1,
                1,
                List.of(),
                List.of()
        );
    }
}
