package com.foliolens.backend.disclosure.infrastructure.extraction.facility;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.infrastructure.chunking.LogicalTableCell;
import com.foliolens.backend.disclosure.infrastructure.chunking.LogicalTableGrid;
import com.foliolens.backend.disclosure.infrastructure.chunking.LogicalTableRow;
import com.foliolens.backend.disclosure.infrastructure.chunking.TableLogicalGridBuilder;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ParsedDisclosureTable에서 시설투자 Fact의 정확한 행·값 셀을 찾아
 * CANDIDATE Evidence를 만든다.
 *
 * 이 클래스는 값의 숫자·날짜 정규화나 VERIFIED 승격을 수행하지 않는다.
 */
@Component
public class FacilityInvestmentEvidenceExtractor {

    public static final String EXTRACTOR_VERSION = "facility-evidence-v1";

    private static final Pattern RAW_UNIT_PATTERN = Pattern.compile(
            "\\(\\s*(원|천원|백만원|억원|%|퍼센트)\\s*\\)"
    );

    // 정정공시의 "정정사항" 비교표는 "정정항목 | 정정전 | 정정후" 헤더
    // 행 뒤로 항목마다 값이 두 개(정정전·정정후)다. 이 헤더가 있는
    // 표에서만 아래 정정 전/후 추출을 적용해, 일반 표의 3셀 행(예:
    // "2. 투자내역 | 투자금액(원) | 5,296,200,000,000")이 잘못 걸리지
    // 않게 한다.
    private static final String CORRECTION_ITEM_HEADER = "정정항목";
    private static final String CORRECTION_BEFORE_HEADER = "정정전";
    private static final String CORRECTION_AFTER_HEADER = "정정후";
    private static final Pattern CORRECTION_LABEL_PREFIX_PATTERN =
            Pattern.compile("^\\d+\\s*[.)]\\s*");
    private static final Pattern CORRECTION_LABEL_SPLIT_PATTERN =
            Pattern.compile("[\\s\\-_:()·%,]+");
    private static final Set<String> CORRECTION_LABEL_NOISE = Set.of(
            "투자내역", "투자기간", "의", "등", "원"
    );
    private static final String CORRECTION_AMOUNT_TOKEN = "투자금액";
    private static final String CORRECTION_END_DATE_TOKEN = "종료일";

    private final TableLogicalGridBuilder gridBuilder;

    public FacilityInvestmentEvidenceExtractor(
            TableLogicalGridBuilder gridBuilder
    ) {
        this.gridBuilder = Objects.requireNonNull(
                gridBuilder,
                "gridBuilder는 필수입니다."
        );
    }

    public FacilityInvestmentEvidenceExtractionResult extract(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable rootTable
    ) {
        Objects.requireNonNull(context, "context는 필수입니다.");
        Objects.requireNonNull(rootTable, "rootTable은 필수입니다.");

        List<FacilityInvestmentEvidenceExtractionResult> results =
                new ArrayList<>();
        extractRecursively(context, rootTable, null, results);

        FacilityInvestmentEvidenceExtractionResult combined =
                FacilityInvestmentEvidenceExtractionResult.combine(results);
        if (combined.candidateCount() == 0) {
            return new FacilityInvestmentEvidenceExtractionResult(
                    combined.candidates(),
                    List.of("시설투자 Evidence 후보를 찾지 못했습니다.")
            );
        }
        return combined;
    }

    private void extractRecursively(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable table,
            String nestingPath,
            List<FacilityInvestmentEvidenceExtractionResult> results
    ) {
        results.add(extractCurrentTable(context, table, nestingPath));

        for (ParsedDisclosureTableRow row : table.rows()) {
            for (ParsedDisclosureTableCell cell : row.cells()) {
                for (int nestedIndex = 0;
                     nestedIndex < cell.nestedTables().size();
                     nestedIndex++) {
                    String childPath = appendNestingPath(
                            nestingPath,
                            row.rowIndex(),
                            cell.cellIndex(),
                            nestedIndex
                    );
                    extractRecursively(
                            context,
                            cell.nestedTables().get(nestedIndex),
                            childPath,
                            results
                    );
                }
            }
        }
    }

    private FacilityInvestmentEvidenceExtractionResult extractCurrentTable(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable table,
            String nestingPath
    ) {
        LogicalTableGrid grid = gridBuilder.build(table);
        EnumMap<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                candidates = new EnumMap<>(FacilityInvestmentFactDefinition.class);

        int correctionHeaderRowIndex =
                findCorrectionDiffHeaderRowIndex(grid);

        for (LogicalTableRow row : grid.rows()) {
            if (correctionHeaderRowIndex >= 0
                    && row.rowIndex() >= correctionHeaderRowIndex) {
                extractCorrectionDiffRow(
                        context,
                        table,
                        nestingPath,
                        row,
                        candidates
                );
            } else {
                extractRow(context, table, nestingPath, row, candidates);
            }
        }

        return new FacilityInvestmentEvidenceExtractionResult(
                candidates,
                List.of()
        );
    }

    /**
     * "정정항목 | 정정전 | 정정후" 헤더 행을 찾는다. 있으면 그 행부터는
     * 정정 전/후 비교 행으로 보고 {@link #extractRow}가 아니라
     * {@link #extractCorrectionDiffRow}로 처리한다. 없으면 -1을
     * 반환해 표 전체를 기존 방식으로만 처리한다.
     */
    private int findCorrectionDiffHeaderRowIndex(LogicalTableGrid grid) {
        for (LogicalTableRow row : grid.rows()) {
            List<LogicalTableCell> cells = row.cells().stream()
                    .filter(LogicalTableCell::isOrigin)
                    .filter(cell -> cell.text() != null
                            && !cell.text().isBlank())
                    .toList();
            if (cells.size() == 3
                    && CORRECTION_ITEM_HEADER.equals(
                            cells.get(0).text().strip()
                    )
                    && CORRECTION_BEFORE_HEADER.equals(
                            cells.get(1).text().strip()
                    )
                    && CORRECTION_AFTER_HEADER.equals(
                            cells.get(2).text().strip()
                    )) {
                return row.rowIndex();
            }
        }
        return -1;
    }

    /**
     * "정정항목 | 정정전 | 정정후" 표의 항목 행 하나에서 정정 전·후
     * Evidence를 함께 만든다. 항목 라벨이 "투자금액" 또는 "종료일"
     * 하나로만 분류되는 행만 다루고, 여러 항목이 한 행에 묶여 있거나
     * (예: "투자금액 - 자기자본대비" 결합 행) 대상이 아닌 행은
     * 추측하지 않고 건너뛴다.
     */
    private void extractCorrectionDiffRow(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable table,
            String nestingPath,
            LogicalTableRow row,
            Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                    candidates
    ) {
        List<LogicalTableCell> cells = row.cells().stream()
                .filter(LogicalTableCell::isOrigin)
                .filter(cell -> cell.text() != null && !cell.text().isBlank())
                .toList();

        if (cells.size() < 3) {
            return;
        }

        List<LogicalTableCell> labelCells = cells.subList(
                0,
                cells.size() - 2
        );
        LogicalTableCell beforeCell = cells.get(cells.size() - 2);
        LogicalTableCell afterCell = cells.get(cells.size() - 1);

        String itemLabel = labelCells.stream()
                .map(LogicalTableCell::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        CorrectionDiffTarget target = classifyCorrectionItem(itemLabel);
        if (target == null) {
            return;
        }

        candidates.computeIfAbsent(
                target.beforeDefinition(),
                ignored -> new ArrayList<>()
        ).add(createEvidence(
                context,
                table,
                nestingPath,
                row,
                cells,
                labelCells,
                beforeCell,
                target.beforeDefinition()
        ));
        candidates.computeIfAbsent(
                target.afterDefinition(),
                ignored -> new ArrayList<>()
        ).add(createEvidence(
                context,
                table,
                nestingPath,
                row,
                cells,
                labelCells,
                afterCell,
                target.afterDefinition()
        ));
    }

    /**
     * "2. 투자내역_투자금액(원)", "4. 투자기간 (종료일)"처럼 접두 번호·
     * 구분자·단위 표기가 문서마다 제각각인 정정항목 라벨을 토큰화해
     * "투자금액" 또는 "종료일" 단일 항목인지 판정한다. 두 항목이 한
     * 행에 결합돼 있으면(예: "투자금액 - 자기자본대비") 토큰이 2개
     * 이상 남아 null을 반환해 추측하지 않는다.
     */
    private CorrectionDiffTarget classifyCorrectionItem(String itemLabel) {
        if (itemLabel == null || itemLabel.isBlank()) {
            return null;
        }

        String stripped = CORRECTION_LABEL_PREFIX_PATTERN
                .matcher(itemLabel.strip())
                .replaceFirst("");
        List<String> tokens = new ArrayList<>();
        for (String token
                : CORRECTION_LABEL_SPLIT_PATTERN.split(stripped)) {
            if (token.isBlank() || CORRECTION_LABEL_NOISE.contains(token)) {
                continue;
            }
            tokens.add(token);
        }

        if (tokens.size() != 1) {
            return null;
        }
        if (CORRECTION_AMOUNT_TOKEN.equals(tokens.get(0))) {
            return new CorrectionDiffTarget(
                    FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_BEFORE,
                    FacilityInvestmentFactDefinition.AMOUNT_CORRECTION_AFTER
            );
        }
        if (CORRECTION_END_DATE_TOKEN.equals(tokens.get(0))) {
            return new CorrectionDiffTarget(
                    FacilityInvestmentFactDefinition
                            .END_DATE_CORRECTION_BEFORE,
                    FacilityInvestmentFactDefinition
                            .END_DATE_CORRECTION_AFTER
            );
        }
        return null;
    }

    private record CorrectionDiffTarget(
            FacilityInvestmentFactDefinition beforeDefinition,
            FacilityInvestmentFactDefinition afterDefinition
    ) {
    }

    private void extractRow(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable table,
            String nestingPath,
            LogicalTableRow row,
            Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                    candidates
    ) {
        List<LogicalTableCell> cells = row.cells().stream()
                .filter(LogicalTableCell::isOrigin)
                .filter(cell -> cell.text() != null && !cell.text().isBlank())
                .toList();

        if (cells.size() < 2) {
            return;
        }

        LogicalTableCell valueCell = cells.getLast();
        List<LogicalTableCell> labelCells = cells.subList(
                0,
                cells.size() - 1
        );

        for (FacilityInvestmentFactDefinition definition
                : FacilityInvestmentFactDefinition.values()) {
            if (!matches(definition, labelCells)) {
                continue;
            }

            DisclosureEvidence evidence = createEvidence(
                    context,
                    table,
                    nestingPath,
                    row,
                    cells,
                    labelCells,
                    valueCell,
                    definition
            );
            candidates.computeIfAbsent(
                    definition,
                    ignored -> new ArrayList<>()
            ).add(evidence);
        }
    }

    private boolean matches(
            FacilityInvestmentFactDefinition definition,
            List<LogicalTableCell> labelCells
    ) {
        boolean rowLabelMatches = labelCells.stream()
                .map(LogicalTableCell::text)
                .anyMatch(definition::matchesRowLabel);

        if (!rowLabelMatches) {
            return false;
        }

        if (definition.columnLabels().isEmpty()) {
            return true;
        }

        return labelCells.stream()
                .map(LogicalTableCell::text)
                .anyMatch(definition::matchesColumnLabel);
    }

    private DisclosureEvidence createEvidence(
            FacilityInvestmentExtractionContext context,
            ParsedDisclosureTable table,
            String nestingPath,
            LogicalTableRow row,
            List<LogicalTableCell> allCells,
            List<LogicalTableCell> labelCells,
            LogicalTableCell valueCell,
            FacilityInvestmentFactDefinition definition
    ) {
        String rowLabel = labelCells.stream()
                .map(LogicalTableCell::text)
                .distinct()
                .reduce((left, right) -> left + " > " + right)
                .orElseThrow();
        String columnLabel = resolveColumnLabel(definition, labelCells);
        String rawValue = valueCell.text();
        String rawUnit = resolveRawUnit(labelCells);
        String sourceText = allCells.stream()
                .map(LogicalTableCell::text)
                .reduce((left, right) -> left + " | " + right)
                .orElseThrow();

        SourceLines sourceLines = resolveSourceLines(row, valueCell);
        int sourceCellIndex = valueCell.sourceCell().cellIndex();

        return new DisclosureEvidence(
                deterministicEvidenceId(
                        context,
                        definition,
                        nestingPath,
                        row.rowIndex(),
                        sourceCellIndex
                ),
                context.disclosureId(),
                context.disclosureDocumentId(),
                context.receiptNo(),
                context.documentName(),
                context.documentFileRole(),
                context.eventDocumentRole(),
                context.sectionId(),
                context.sectionPath(),
                context.contentBlockId(),
                EvidenceBlockType.TABLE_CELL,
                "table-" + table.order(),
                new DisclosureEvidenceLocation(
                        sourceLines.start(),
                        sourceLines.end(),
                        nestingPath,
                        row.rowIndex(),
                        sourceCellIndex
                ),
                new DisclosureEvidenceValue(
                        sourceText,
                        rowLabel,
                        columnLabel,
                        rawValue,
                        rawUnit,
                        null
                ),
                EvidenceStatus.CANDIDATE
        );
    }

    private String resolveColumnLabel(
            FacilityInvestmentFactDefinition definition,
            List<LogicalTableCell> labelCells
    ) {
        if (definition.columnLabels().isEmpty()) {
            return null;
        }
        return labelCells.stream()
                .map(LogicalTableCell::text)
                .filter(definition::matchesColumnLabel)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String resolveRawUnit(List<LogicalTableCell> labelCells) {
        for (int index = labelCells.size() - 1; index >= 0; index--) {
            Matcher matcher = RAW_UNIT_PATTERN.matcher(
                    labelCells.get(index).text()
            );
            if (matcher.find()) {
                return "퍼센트".equals(matcher.group(1))
                        ? "%"
                        : matcher.group(1);
            }
        }
        return null;
    }

    private SourceLines resolveSourceLines(
            LogicalTableRow row,
            LogicalTableCell valueCell
    ) {
        int cellStart = valueCell.sourceCell().sourceLineStart();
        int cellEnd = valueCell.sourceCell().sourceLineEnd();
        if (cellStart >= 0 && cellEnd >= cellStart) {
            return new SourceLines(cellStart, cellEnd);
        }

        if (row.sourceLineStart() >= 0
                && row.sourceLineEnd() >= row.sourceLineStart()) {
            return new SourceLines(
                    row.sourceLineStart(),
                    row.sourceLineEnd()
            );
        }
        return new SourceLines(-1, -1);
    }

    private UUID deterministicEvidenceId(
            FacilityInvestmentExtractionContext context,
            FacilityInvestmentFactDefinition definition,
            String nestingPath,
            int rowIndex,
            int cellIndex
    ) {
        String key = context.disclosureDocumentId()
                + "|" + context.contentBlockId()
                + "|" + Objects.toString(nestingPath, "root")
                + "|" + rowIndex
                + "|" + cellIndex
                + "|" + definition.factKey()
                + "|" + EXTRACTOR_VERSION;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private String appendNestingPath(
            String parentPath,
            int rowIndex,
            int cellIndex,
            int nestedIndex
    ) {
        String segment = "rows[" + rowIndex + "]"
                + ".cells[" + cellIndex + "]"
                + ".nestedTables[" + nestedIndex + "]";
        return parentPath == null || parentPath.isBlank()
                ? segment
                : parentPath + "." + segment;
    }

    private record SourceLines(int start, int end) {
    }
}
