package com.foliolens.backend.disclosure.infrastructure.extraction.facility;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.infrastructure.chunking.TableLogicalGridBuilder;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityInvestmentEvidenceExtractorTest {

    private final FacilityInvestmentEvidenceExtractor extractor =
            new FacilityInvestmentEvidenceExtractor(
                    new TableLogicalGridBuilder()
            );

    @Test
    void 대표_시설투자_표에서_핵심_8개와_투자유형을_추출한다() {
        FacilityInvestmentExtractionContext context = context();
        ParsedDisclosureTable table = goldenFacilityTable();

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context, table);

        assertThat(result.candidateCount()).isEqualTo(9);
        assertThat(result.hasAllCoreCandidates()).isTrue();
        assertThat(result.ambiguousDefinitions()).isEmpty();

        DisclosureEvidence amount = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.AMOUNT
        ).orElseThrow();
        assertThat(amount.status()).isEqualTo(EvidenceStatus.CANDIDATE);
        assertThat(amount.value().rowLabel())
                .isEqualTo("2. 투자내역 > 투자금액(원)");
        assertThat(amount.value().rawValue())
                .isEqualTo("5,296,200,000,000");
        assertThat(amount.value().rawUnit()).isEqualTo("원");
        assertThat(amount.value().sourceText())
                .isEqualTo(
                        "2. 투자내역 | 투자금액(원) | 5,296,200,000,000"
                );
        assertThat(amount.location().tableRowIndex()).isEqualTo(2);
        assertThat(amount.location().tableCellIndex()).isEqualTo(2);

        DisclosureEvidence ratio = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.EQUITY_RATIO
        ).orElseThrow();
        assertThat(ratio.value().rawValue()).isEqualTo("9.90");
        assertThat(ratio.value().rawUnit()).isEqualTo("%");

        DisclosureEvidence startDate = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.START_DATE
        ).orElseThrow();
        assertThat(startDate.value().columnLabel()).isEqualTo("시작일");
        assertThat(startDate.value().rawValue()).isEqualTo("2024-04-24");

        DisclosureEvidence endDate = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.END_DATE
        ).orElseThrow();
        assertThat(endDate.value().columnLabel()).isEqualTo("종료일");
        assertThat(endDate.value().rawValue()).isEqualTo("2026-10-30");

        DisclosureEvidence purpose = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.PURPOSE
        ).orElseThrow();
        assertThat(purpose.value().rawValue()).contains("차세대 DRAM");
    }

    @Test
    void 같은_입력을_재추출하면_Evidence_ID도_같다() {
        FacilityInvestmentExtractionContext context = context();
        ParsedDisclosureTable table = goldenFacilityTable();

        UUID first = extractor.extract(context, table)
                .uniqueCandidate(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow()
                .evidenceId();
        UUID second = extractor.extract(context, table)
                .uniqueCandidate(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow()
                .evidenceId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 같은_Fact_레이블이_두번_나오면_임의로_고르지_않는다() {
        ParsedDisclosureTable table = table(
                row(0, cell(0, 1, 1, "투자금액(원)"),
                        cell(1, 1, 1, "100")),
                row(1, cell(0, 1, 1, "투자금액(원)"),
                        cell(1, 1, 1, "200"))
        );

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context(), table);

        assertThat(result.candidatesFor(
                FacilityInvestmentFactDefinition.AMOUNT
        )).hasSize(2);
        assertThat(result.uniqueCandidate(
                FacilityInvestmentFactDefinition.AMOUNT
        )).isEmpty();
        assertThat(result.ambiguousDefinitions())
                .containsExactly(FacilityInvestmentFactDefinition.AMOUNT);
    }

    @Test
    void 중첩_표_Evidence에는_중첩_경로를_보존한다() {
        ParsedDisclosureTable nested = new ParsedDisclosureTable(
                2,
                20,
                30,
                List.of(row(
                        0,
                        cell(0, 1, 1, "투자금액(원)"),
                        cell(1, 1, 1, "300")
                ))
        );
        ParsedDisclosureTableCell nestedContainer =
                new ParsedDisclosureTableCell(
                        1,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        null,
                        10,
                        30,
                        List.of(nested),
                        List.of()
                );
        ParsedDisclosureTable root = table(
                row(
                        0,
                        cell(0, 1, 1, "상세내역"),
                        nestedContainer
                )
        );

        DisclosureEvidence evidence = extractor.extract(context(), root)
                .uniqueCandidate(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow();

        assertThat(evidence.location().tableNestingPath())
                .isEqualTo("rows[0].cells[1].nestedTables[0]");
        assertThat(evidence.value().rawValue()).isEqualTo("300");
    }

    @Test
    void 레이블만_있고_값_셀이_없으면_후보를_만들지_않는다() {
        ParsedDisclosureTable table = table(
                row(0, cell(0, 1, 1, "투자금액(원)"))
        );

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context(), table);

        assertThat(result.candidateCount()).isZero();
        assertThat(result.missingCoreDefinitions())
                .contains(FacilityInvestmentFactDefinition.AMOUNT);
        assertThat(result.warnings())
                .containsExactly("시설투자 Evidence 후보를 찾지 못했습니다.");
    }

    // ---- 정정사항 비교표(정정항목 | 정정전 | 정정후) ----
    // 아래 표 구조는 실제 접수번호 20240813800252/20231228800377의
    // 원문 표 그대로다.

    @Test
    void 정정사항_비교표에서_정정_전후_금액과_종료일을_함께_추출한다() {
        ParsedDisclosureTable table = table(
                row(0, cell(0, 1, 1, "1. 정정관련 공시서류"),
                        cell(1, 1, 1, "신규 시설투자 등(자율공시)")),
                row(1, cell(0, 1, 1, "2. 정정관련 공시서류제출일"),
                        cell(1, 1, 1, "2024-08-13")),
                row(2, cell(0, 1, 1, "3. 정정사유"),
                        cell(1, 1, 1, "투자금액 및 투자기간 변경")),
                row(3, cell(0, 1, 1, "4. 정정사항")),
                row(4, cell(0, 1, 1, "정정항목"),
                        cell(1, 1, 1, "정정전"),
                        cell(2, 1, 1, "정정후")),
                row(5, cell(0, 1, 1, "2. 투자금액"),
                        cell(1, 1, 1, "80,300,000,000"),
                        cell(2, 1, 1, "100,800,000,000")),
                row(6, cell(0, 1, 1, "4. 투자기간 종료일"),
                        cell(1, 1, 1, "2025-09-30"),
                        cell(2, 1, 1, "2025-10-31"))
        );

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context(), table);

        DisclosureEvidence amountBefore = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_BEFORE
        ).orElseThrow();
        DisclosureEvidence amountAfter = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_AFTER
        ).orElseThrow();
        DisclosureEvidence endDateBefore = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.END_DATE_CORRECTION_BEFORE
        ).orElseThrow();
        DisclosureEvidence endDateAfter = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.END_DATE_CORRECTION_AFTER
        ).orElseThrow();

        assertThat(amountBefore.value().rawValue())
                .isEqualTo("80,300,000,000");
        assertThat(amountAfter.value().rawValue())
                .isEqualTo("100,800,000,000");
        assertThat(endDateBefore.value().rawValue()).isEqualTo("2025-09-30");
        assertThat(endDateAfter.value().rawValue()).isEqualTo("2025-10-31");

        // 이 표에는 "일반 행 매칭" 경로로도 매칭될 법한 "투자금액" 라벨이
        // 있지만, 정정사항 표로 인식된 뒤부터는 일반 경로로 처리하지
        // 않으므로 facility.amount 핵심 Fact 후보를 만들지 않는다.
        assertThat(result.candidatesFor(FacilityInvestmentFactDefinition.AMOUNT))
                .isEmpty();
    }

    @Test
    void 정정항목이_여러_값을_묶은_행이면_추측하지_않는다() {
        ParsedDisclosureTable table = table(
                row(0, cell(0, 1, 1, "정정항목"),
                        cell(1, 1, 1, "정정전"),
                        cell(2, 1, 1, "정정후")),
                row(1, cell(0, 1, 1,
                                "2.투자내역 - 투자금액(원) - 자기자본대비(%)"),
                        cell(1, 1, 1, "143,700,000,000 28.14"),
                        cell(2, 1, 1, "155,800,000,000 30.51")),
                row(2, cell(0, 1, 1, "4.투자기간 - 종료일"),
                        cell(1, 1, 1, "2023-12-31"),
                        cell(2, 1, 1, "2025-04-30"))
        );

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context(), table);

        assertThat(result.candidatesFor(
                FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_BEFORE
        )).isEmpty();
        assertThat(result.candidatesFor(
                FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_AFTER
        )).isEmpty();
        // 종료일 행은 결합되지 않았으므로 정상적으로 추출된다.
        assertThat(result.uniqueCandidate(
                FacilityInvestmentFactDefinition.END_DATE_CORRECTION_BEFORE
        )).isPresent();
        assertThat(result.uniqueCandidate(
                FacilityInvestmentFactDefinition.END_DATE_CORRECTION_AFTER
        )).isPresent();
    }

    @Test
    void 정정사항_표가_없으면_일반_행_매칭만_한다() {
        ParsedDisclosureTable table = goldenFacilityTable();

        FacilityInvestmentEvidenceExtractionResult result =
                extractor.extract(context(), table);

        assertThat(result.candidatesFor(
                FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_BEFORE
        )).isEmpty();
        assertThat(result.uniqueCandidate(FacilityInvestmentFactDefinition.AMOUNT))
                .isPresent();
    }

    private FacilityInvestmentExtractionContext context() {
        return new FacilityInvestmentExtractionContext(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "20240424800596",
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

    private ParsedDisclosureTable table(
            ParsedDisclosureTableRow... rows
    ) {
        return new ParsedDisclosureTable(
                1,
                1,
                100,
                List.of(rows)
        );
    }

    private ParsedDisclosureTableRow row(
            int rowIndex,
            ParsedDisclosureTableCell... cells
    ) {
        int line = 10 + rowIndex;
        return new ParsedDisclosureTableRow(
                rowIndex,
                line,
                line,
                List.of(cells)
        );
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
