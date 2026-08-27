package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableContext;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TABLE ContentBlock을 검색용 TABLE 청크 후보로 변환한다.
 *
 * 흐름:
 * DisclosureContentBlock(TABLE)
 *         ↓ JSONB 읽기
 * ParsedDisclosureTable
 *         ↓ 행·열 병합 구조 펼치기
 * LogicalTableGrid
 *         ↓ 검색 가능한 문자열로 직렬화
 * SerializedTable
 *         ↓ 크기 정책에 따라 행 단위 분할
 * TableChunkPart
 *         ↓ 검색 문맥과 원문 출처 추가
 * GeneratedChunkDraft
 */
@Component
public class TableChunkGenerator {

    private static final String ROW_SEPARATOR = "\n";
    private static final String PARENT_CONTEXT_PREFIX = "상위 셀 문맥: ";
    private static final String CONTEXT_TRUNCATION_SUFFIX = "…";

    private static final int MAX_ACCUMULATED_PARENT_CONTEXT_CHARS = 500;

    private final DisclosureChunkingPolicy policy;
    private final DisclosureTablePayloadReader payloadReader;
    private final NestedTableContextSelector contextSelector;
    private final TableLogicalGridBuilder gridBuilder;
    private final TableTextSerializer textSerializer;
    private final ChunkTextNormalizer textNormalizer;
    private final SentenceBoundarySplitter sentenceSplitter;

    public TableChunkGenerator(
            DisclosureChunkingPolicy policy,
            DisclosureTablePayloadReader payloadReader,
            NestedTableContextSelector contextSelector,
            TableLogicalGridBuilder gridBuilder,
            TableTextSerializer textSerializer,
            ChunkTextNormalizer textNormalizer,
            SentenceBoundarySplitter sentenceSplitter
    ) {
        this.policy = Objects.requireNonNull(
                policy,
                "policy는 필수입니다."
        );
        this.payloadReader = Objects.requireNonNull(
                payloadReader,
                "payloadReader는 필수입니다."
        );
        this.contextSelector = Objects.requireNonNull(
                contextSelector,
                "contextSelector는 필수입니다."
        );
        this.gridBuilder = Objects.requireNonNull(
                gridBuilder,
                "gridBuilder는 필수입니다."
        );
        this.textSerializer = Objects.requireNonNull(
                textSerializer,
                "textSerializer는 필수입니다."
        );
        this.textNormalizer = Objects.requireNonNull(
                textNormalizer,
                "textNormalizer는 필수입니다."
        );
        this.sentenceSplitter = Objects.requireNonNull(
                sentenceSplitter,
                "sentenceSplitter는 필수입니다."
        );
    }

    /**
     * 처리 과정:
     * 1. 입력값을 검증
     * 2. DisclosureTablePayloadReader로 JSONB 읽음
     * 3. JSONB를 ParsedDisclosureTable로 복원
     * 4. 최상위 표와 중첩 표를 재귀적으로 처리
     * 5. 만들어진 GeneratedChunkDraft 목록을 반환
     */
    public List<GeneratedChunkDraft> generate(
            UUID documentId,
            UUID sectionId,
            String sectionPath, // III. 재무에 관한 사항 > 연결재무제표 같은 경로
            List<String> headingContexts, // 표 바로 앞에 등장한 제목들
            DisclosureContentBlock tableBlock
    ) {
        Objects.requireNonNull(
                documentId,
                "documentId는 필수입니다."
        );
        Objects.requireNonNull(
                headingContexts,
                "headingContexts는 필수입니다."
        );

        validateHeadingContexts(headingContexts);
        validateTableBlock(documentId, sectionId, tableBlock);

        // DisclosureTablePayloadReader로 JSONB를 읽은 후
        // JSONB를 ParsedDisclosureTable로 복원
        ParsedDisclosureTable rootTable = payloadReader.read(tableBlock);

        // 결과적으로 만들어지는 GeneratedChunkDraft(청크)를 담아두는 리스트
        List<GeneratedChunkDraft> result = new ArrayList<>();

        // 하나의 TABLE 블록에서 생성되는 청크들의 순서를 관리 (블럭의 길이가 길면 하나의 블럭에서 여러개의 청크가 생성될 수 있음)
        PartIndexCounter partIndexCounter = new PartIndexCounter();

        // 현재 표를 청크로 만들고, 표의 셀 안에 중첩 표가 있으면 그것도 다시 처리
        generateRecursively(
                documentId,
                sectionId,
                normalizePath(sectionPath),
                List.copyOf(headingContexts),
                tableBlock,
                rootTable,
                null,
                null,
                partIndexCounter,
                result
        );

        return List.copyOf(result);
    }

    /**
     * 현재 표를 청크로 만들고 셀 내부 중첩 표를 재귀 처리
     *
     * 현재 표 처리:
     * ParsedDisclosureTable
     * → LogicalTableGrid
     * → SerializedTable
     * → 여러 TableChunkPart
     * → GeneratedChunkDraft
     */
    private void generateRecursively(
            UUID documentId,
            UUID sectionId,
            String sectionPath,
            List<String> headingContexts,
            DisclosureContentBlock tableBlock,
            ParsedDisclosureTable table,
            String nestingPath,
            String parentCellContext,
            PartIndexCounter partIndexCounter,
            List<GeneratedChunkDraft> result
    ) {
        // ParsedDisclosureTable(table)을 LogicalTableGrid로 변환
        LogicalTableGrid grid = gridBuilder.build(table);

        // LogicalTableGrid를 SerializedTable로 변환
        SerializedTable serializedTable = textSerializer.serialize(grid);

        if (!serializedTable.isEmpty()) {
            // 직렬화된 표를 청크 크기 정책에 맞게 여러 부분으로 나눔
            List<TableChunkPart> parts = splitIntoParts(serializedTable);

            for (TableChunkPart part : parts) {
                result.add(
                        // 하나의 TableChunkPart를 최종 청크 후보인 GeneratedChunkDraft로 변환
                        createDraft(
                                documentId,
                                sectionId,
                                sectionPath,
                                headingContexts,
                                tableBlock,
                                nestingPath,
                                parentCellContext,
                                partIndexCounter.next(),
                                part
                        )
                );
            }
        }

        /*
         * 부모 표의 직접 텍스트와 중첩 표 내용은 섞지 않는다.
         * 각 nestedTable은 별도 LogicalTableGrid와 TABLE 청크가 된다.
         *
         * cell의 내용에 중첩 표가 포함되어 있는 경우에만 코드 실행
         */
        for (ParsedDisclosureTableRow row : table.rows()) {
            for (ParsedDisclosureTableCell cell : row.cells()) {
                for (
                        int nestedIndex = 0;
                        // cell의 nestedTable이 빈 리스트이면 for문 안돌고 넘어감
                        // 즉 중첩 테이블이 없는 cell은 그냥 넘어감
                        nestedIndex < cell.nestedTables().size();
                        nestedIndex++
                ) {
                    ParsedDisclosureTable nestedTable = cell.nestedTables().get(nestedIndex);

                    String childPath = appendNestingPath(
                            nestingPath,
                            row.rowIndex(),
                            cell.cellIndex(),
                            nestedIndex
                    );

                    String childParentContext =
                            appendParentContext(
                                    parentCellContext,
                                    resolveDirectParentContext(
                                            nestedTable,
                                            cell.text()
                                    )
                            );

                    // 재귀로 처리
                    generateRecursively(
                            documentId,
                            sectionId,
                            sectionPath,
                            headingContexts,
                            tableBlock,
                            nestedTable,
                            childPath,
                            childParentContext,
                            partIndexCounter,
                            result
                    );
                }
            }
        }
    }

    /**
     * 직렬화된 표를 청크 크기 정책에 맞게 여러 부분으로 나눔
     *
     * 원칙:
     *  표를 문자열 길이만 보고 임의의 위치에서 자르지 않습니다.
     *  가능한 한 행 단위로 나눕니다.
     *  표의 선두 HEADER 행은 각 청크에 반복합니다. **
     *  한 행 자체가 너무 길면 그 행만 별도로 문장 경계 기준으로 나눕니다.
     */
    private List<TableChunkPart> splitIntoParts(SerializedTable table) {
        if (table.isEmpty()) {
            return List.of();
        }

        // 표 맨 앞에서 연속해서 등장하는 HEADER 전용 행을 가져옴
        List<SerializedTableRow> headerRows = table.leadingHeaderRows();

        List<SerializedTableRow> bodyRows =
                table.rows().subList(headerRows.size(), table.rows().size());

        /*
         * 표 전체가 HEADER 행으로만 구성됐거나 반복 머리글 자체가
         * 절대 최대 길이를 소진한다면 모든 행을 일반 본문처럼 분할한다.
         *
         * 후자의 경우 머리글을 각 청크에 반복할 수 없지만,
         * 원래 머리글 행을 bodyRows에 포함해 내용과 출처가 누락되지 않게 한다.
         * 긴 단일 머리글 행은 기존 splitOversizedRow 로직이 분할한다.
         */
        if (bodyRows.isEmpty() || cannotRepeatHeaders(headerRows)) {
            headerRows = List.of();
            bodyRows = table.rows();
        }

        List<TableChunkPart> result = new ArrayList<>();

        List<SerializedTableRow> currentRows = new ArrayList<>();

        for (SerializedTableRow row : bodyRows) {
            if (currentRows.isEmpty()) {
                // HEADER와 현재 행 하나만 합쳤는데도 absoluteMaxChars를 초과하는지 검사
                if (requiresOversizedRowSplit(headerRows, row)) {
                    // 한 행 자체가 너무 긴 경우 처리
                    result.addAll(splitOversizedRow(headerRows, row));
                } else {
                    currentRows.add(row);
                }
                continue;
            }

            List<SerializedTableRow> candidateRows = append(currentRows, row);

            String currentText = renderRows(headerRows, currentRows);

            String candidateText = renderRows(headerRows, candidateRows);

            // 청크 정책에서 TABLE 정책을 가져옴
            DisclosureChunkingPolicy.ChunkSizePolicy tablePolicy = policy.table();

            boolean fitsTarget = candidateText.length() <= tablePolicy.targetMaxChars();

            boolean shouldFillShortChunk =
                    currentText.length()
                            < tablePolicy.targetMinChars()
                            && candidateText.length()
                            <= tablePolicy.normalMaxChars();

            if (fitsTarget || shouldFillShortChunk) {
                currentRows.add(row);
                continue;
            }

            // 일반적인 여러 행을 하나의 TableChunkPart로 만든 후 result에 추가
            result.add(createNormalPart(headerRows, currentRows));

            currentRows.clear();

            if (requiresOversizedRowSplit(headerRows, row)) {
                result.addAll(splitOversizedRow(headerRows, row));
            } else {
                currentRows.add(row);
            }
        }

        if (!currentRows.isEmpty()) {
            // 일반적인 여러 행을 하나의 TableChunkPart로 만든 후 result에 추가
            result.add(createNormalPart(headerRows, currentRows));
        }

        return List.copyOf(result);
    }

    /**
     * 반복 머리글 뒤에 본문을 한 글자도 넣을 수 없는지 확인한다.
     */
    private boolean cannotRepeatHeaders(
            List<SerializedTableRow> headerRows
    ) {
        if (headerRows.isEmpty()) {
            return false;
        }

        String headerText = renderRows(headerRows, List.of());

        int prefixLength = headerText.length()
                + ROW_SEPARATOR.length();

        return prefixLength
                >= policy.table().absoluteMaxChars();
    }

    /**
     * HEADER와 현재 행 하나만 합쳤는데도 absoluteMaxChars를 초과하는지 검사
     * (HEADER + 행 하나 > 절대 최대 길이) 라면 true 반환
     */
    private boolean requiresOversizedRowSplit(
            List<SerializedTableRow> headerRows,
            SerializedTableRow row
    ) {
        String bodyText = renderRows(headerRows, List.of(row));

        return bodyText.length()
                > policy.table().absoluteMaxChars();
    }

    /**
     * 머리글을 제외한 가용 길이 안에서 긴 단일 행을 분할한다.
     *
     * 각 조각은 같은 원본 rowIndex를 출처로 갖는다.
     */
    private List<TableChunkPart> splitOversizedRow(
            List<SerializedTableRow> headerRows,
            SerializedTableRow row
    ) {
        String headerText = renderRows(
                headerRows,
                List.of()
        );

        int prefixLength = headerText.isBlank()
                ? 0
                : headerText.length()
                  + ROW_SEPARATOR.length();

        DisclosureChunkingPolicy.ChunkSizePolicy
                tablePolicy = policy.table();

        int availableAbsolute =
                tablePolicy.absoluteMaxChars()
                        - prefixLength;

        if (availableAbsolute < 1) {
            throw new IllegalStateException(
                    "TABLE 머리글만으로 절대 최대 길이를 초과했습니다."
                            + " headerLength=" + headerText.length()
            );
        }

        int availablePreferred = Math.min(
                Math.max(
                        1,
                        tablePolicy.normalMaxChars()
                                - prefixLength
                ),
                availableAbsolute
        );

        List<String> fragments =
                sentenceSplitter.split(
                        row.text(),
                        availablePreferred,
                        availableAbsolute
                );

        List<TableChunkPart> result =
                new ArrayList<>(fragments.size());

        for (String fragment : fragments) {
            String bodyText = headerText.isBlank()
                    ? fragment
                    : headerText
                      + ROW_SEPARATOR
                      + fragment;

            result.add(
                    new TableChunkPart(
                            bodyText,
                            headerRows,
                            List.of(row)
                    )
            );
        }

        return List.copyOf(result);
    }

    // 일반적인 여러 행을 하나의 TableChunkPart로 만듦
    private TableChunkPart createNormalPart(
            List<SerializedTableRow> headerRows,
            List<SerializedTableRow> bodyRows
    ) {
        return new TableChunkPart(
                renderRows(headerRows, bodyRows),
                headerRows,
                bodyRows
        );
    }

    /**
     * 하나의 TableChunkPart를 최종 청크 후보인 GeneratedChunkDraft로 변환
     */
    private GeneratedChunkDraft createDraft(
            UUID documentId,
            UUID sectionId,
            String sectionPath,
            List<String> headingContexts,
            DisclosureContentBlock tableBlock,
            String nestingPath,
            String parentCellContext,
            int partIndex,
            TableChunkPart part
    ) {
        // 중첩 표에 상위 셀 문맥을 추가
        // 예를 들어 사업부문별 실적이라는 셀 안에 중첩 표가 있다면 검색 내용은 다음처럼 만들어짐
        //
        // 상위 셀 문맥: 사업부문별 실적
        // 구분 | 매출액 | 영업이익
        // 반도체 | 100억 | 20억
        String searchBody = createSearchBody(parentCellContext, part.bodyText());

        String searchText =
                textNormalizer.buildSearchText(
                        sectionPath,
                        headingContexts,
                        searchBody
                );

        // 청크가 원본 표의 어디에서 만들어졌는지 기록ㄹ
        List<GeneratedChunkSource> sources = createSources(tableBlock, nestingPath, part);

        return new GeneratedChunkDraft(
                documentId,
                sectionId,
                sectionPath,
                DisclosureChunkType.TABLE,
                tableBlock.getSequenceNo(),
                partIndex, // 같은 TABLE 블록에서 몇 번째로 만들어진 청크인지
                part.bodyText(), // 실제 표 내용
                searchText, // 섹션·제목·상위 셀 문맥까지 포함한 검색용 내용
                sources // 이 청크가 원본 표의 몇 번째 행에서 왔는지
        );
    }

    /**
     * 반복한 HEADER 행과 실제 본문 행의 출처를 각각 보존한다.
     */
    private List<GeneratedChunkSource> createSources(
            DisclosureContentBlock tableBlock,
            String nestingPath,
            TableChunkPart part
    ) {
        List<GeneratedChunkSource> result = new ArrayList<>(2);

        if (!part.headerRows().isEmpty()) {
            result.add(
                    createTableSource( // 특정 행 목록을 하나의 GeneratedChunkSource로 변환
                            tableBlock,
                            nestingPath,
                            part.headerRows()
                    )
            );
        }

        if (!part.bodyRows().isEmpty()) {
            result.add(
                    createTableSource( // 특정 행 목록을 하나의 GeneratedChunkSource로 변환
                            tableBlock,
                            nestingPath,
                            part.bodyRows()
                    )
            );
        }

        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "TABLE 청크 출처가 비어 있습니다."
            );
        }

        return List.copyOf(result);
    }

    // 특정 행 목록을 하나의 GeneratedChunkSource로 변환
    private GeneratedChunkSource createTableSource(
            DisclosureContentBlock tableBlock,
            String nestingPath,
            List<SerializedTableRow> rows
    ) {
        int rowIndexStart = rows.stream()
                .mapToInt(SerializedTableRow::rowIndex)
                .min()
                .orElseThrow();

        int rowIndexEnd = rows.stream()
                .mapToInt(SerializedTableRow::rowIndex)
                .max()
                .orElseThrow();

        int sourceLineStart = rows.stream()
                .mapToInt(SerializedTableRow::sourceLineStart)
                .filter(line -> line >= 0)
                .min()
                .orElse(-1);

        int sourceLineEnd = rows.stream()
                .mapToInt(SerializedTableRow::sourceLineEnd)
                .filter(line -> line >= 0)
                .max()
                .orElse(-1);

        return GeneratedChunkSource.tableRows(
                tableBlock.getId(),
                tableBlock.getSequenceNo(),
                sourceLineStart,
                sourceLineEnd,
                nestingPath,
                rowIndexStart,
                rowIndexEnd
        );
    }

    /**
     * 문자열 조립 보조 메서드
     */
    // HEADER 행과 본문 행을 문자열로 합침
    private String renderRows(
            List<SerializedTableRow> headerRows,
            List<SerializedTableRow> bodyRows
    ) {
        List<String> values = new ArrayList<>(
                headerRows.size() + bodyRows.size()
        );

        headerRows.stream()
                .map(SerializedTableRow::text)
                .filter(value -> !value.isBlank())
                .forEach(values::add);

        bodyRows.stream()
                .map(SerializedTableRow::text)
                .filter(value -> !value.isBlank())
                .forEach(values::add);

        return String.join(
                ROW_SEPARATOR,
                values
        );
    }

    // 현재까지 모은 행 목록에 다음 행 하나를 추가한 새로운 목록을 만듦
    private List<SerializedTableRow> append(
            List<SerializedTableRow> rows,
            SerializedTableRow nextRow
    ) {
        List<SerializedTableRow> result =
                new ArrayList<>(rows.size() + 1);

        result.addAll(rows);
        result.add(nextRow);

        return result;
    }

    /**
     * 중첩 표에 상위 셀 문맥을 추가
     *
     * 상위 셀 문맥: 사업부문별 실적
     * 구분 | 매출액 | 영업이익
     * 반도체 | 100억 | 20억
     *
     * 최상위 표처럼 상위 셀 문맥이 없으면 표 본문만 반환
     */
    private String createSearchBody(String parentCellContext, String bodyText) {
        String normalizedContext =
                textNormalizer.normalizeHeading(parentCellContext);

        if (normalizedContext.length()
                > MAX_ACCUMULATED_PARENT_CONTEXT_CHARS) {
            throw new IllegalStateException(
                    "누적 상위 셀 문맥이 최대 길이를 초과했습니다."
                            + " length=" + normalizedContext.length()
            );
        }

        if (normalizedContext.isBlank()) {
            return bodyText;
        }

        return PARENT_CONTEXT_PREFIX
                + normalizedContext
                + ROW_SEPARATOR
                + bodyText;
    }

    private String appendNestingPath(
            String parentPath,
            int rowIndex,
            int cellIndex,
            int nestedIndex
    ) {
        String currentPath =
                "rows[" + rowIndex + "]"
                        + ".cells[" + cellIndex + "]"
                        + ".nestedTables[" + nestedIndex + "]";

        if (parentPath == null || parentPath.isBlank()) {
            return currentPath;
        }

        return parentPath + "." + currentPath;
    }

    private String appendParentContext(
            String parentContext,
            String currentContext
    ) {
        String normalizedParent = textNormalizer.normalizeHeading(parentContext);

        String normalizedCurrent = textNormalizer.normalizeHeading(currentContext);

        if (normalizedParent.isBlank()) {
            return normalizedCurrent;
        }

        if (normalizedCurrent.isBlank()) {
            return selectNearestAncestorContext(
                    normalizedParent,
                    MAX_ACCUMULATED_PARENT_CONTEXT_CHARS
            );
        }

        /*
         * Selector가 고른 가장 가까운 직접 부모 문맥을 전부 보존하고,
         * 남은 길이에 조상 문맥의 끝부분부터 넣는다.
         */
        int parentLimit = MAX_ACCUMULATED_PARENT_CONTEXT_CHARS
                - SectionPathResolver.PATH_SEPARATOR.length()
                - normalizedCurrent.length();

        if (parentLimit < 1) {
            return selectNearestAncestorContext(
                    normalizedCurrent,
                    MAX_ACCUMULATED_PARENT_CONTEXT_CHARS
            );
        }

        String limitedParent = selectNearestAncestorContext(
                normalizedParent,
                parentLimit
        );

        if (limitedParent.isBlank()) {
            return normalizedCurrent;
        }

        return limitedParent
                + SectionPathResolver.PATH_SEPARATOR
                + normalizedCurrent;
    }

    /**
     * v2 TABLE은 파서가 보존한 표 앞·뒤 문맥을 사용한다.
     * parentContext가 없는 v1 TABLE만 부모 셀 전체 텍스트를
     * 기존 방식으로 제한해 사용한다.
     */
    private String resolveDirectParentContext(
            ParsedDisclosureTable nestedTable,
            String legacyParentCellText
    ) {
        ParsedDisclosureTableContext sourceContext =
                nestedTable.parentContext();

        if (sourceContext == null) {
            return limitLegacyContext(
                    textNormalizer.normalizeHeading(
                            legacyParentCellText
                    ),
                    NestedTableContextSelector
                            .MAX_SELECTED_CONTEXT_CHARS
            );
        }

        ParsedDisclosureTableContext selectedContext =
                contextSelector.select(sourceContext);

        String precedingText = textNormalizer.normalizeHeading(
                selectedContext.precedingText()
        );
        String followingText = textNormalizer.normalizeHeading(
                selectedContext.followingText()
        );

        String directContext;

        if (precedingText.isBlank()) {
            directContext = followingText;
        } else if (followingText.isBlank()) {
            directContext = precedingText;
        } else {
            directContext = precedingText
                    + ROW_SEPARATOR
                    + followingText;
        }

        String normalizedDirectContext =
                textNormalizer.normalizeHeading(directContext);

        if (normalizedDirectContext.length()
                > NestedTableContextSelector
                .MAX_SELECTED_CONTEXT_CHARS) {
            throw new IllegalStateException(
                    "Selector의 직접 부모 문맥이 최대 길이를 초과했습니다."
                            + " length="
                            + normalizedDirectContext.length()
            );
        }

        return normalizedDirectContext;
    }

    /**
     * 여러 단계의 부모 문맥 중 현재 표에 가까운 끝부분을 선택한다.
     */
    private String selectNearestAncestorContext(
            String value,
            int maxChars
    ) {
        if (value == null || value.isBlank() || maxChars < 1) {
            return "";
        }

        String normalized = textNormalizer.normalizeHeading(value);

        if (normalized.length() <= maxChars) {
            return normalized;
        }

        if (maxChars == 1) {
            return CONTEXT_TRUNCATION_SUFFIX;
        }

        List<String> parts = sentenceSplitter.split(
                normalized,
                maxChars - CONTEXT_TRUNCATION_SUFFIX.length(),
                maxChars - CONTEXT_TRUNCATION_SUFFIX.length()
        );

        if (parts.isEmpty()) {
            return "";
        }

        return CONTEXT_TRUNCATION_SUFFIX
                + parts.getLast();
    }

    /**
     * v1 TABLE에는 표 앞·뒤 위치 정보가 없으므로 기존 동작을 유지한다.
     * 부모 셀 전체 텍스트의 앞부분을 보존하되 가능하면 단어 경계에서 자른다.
     * 잘린 문맥에는 말줄임표를 붙여 원문 전체가 아님을 표시한다.
     */
    private String limitLegacyContext(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.strip();

        if (normalized.length() <= maxChars) {
            return normalized;
        }

        int contentLimit = maxChars
                - CONTEXT_TRUNCATION_SUFFIX.length();

        int boundary = normalized.lastIndexOf(' ', contentLimit);

        /*
         * 너무 앞에서 발견된 공백 때문에 핵심 문맥이 과도하게 짧아지면
         * 최대 길이 지점에서 자른다.
         */
        if (boundary < contentLimit / 2) {
            boundary = contentLimit;
        }

        if (boundary > 0
                && Character.isHighSurrogate(
                normalized.charAt(boundary - 1)
        )) {
            boundary--;
        }

        return normalized.substring(0, boundary)
                .stripTrailing()
                + CONTEXT_TRUNCATION_SUFFIX;
    }

    private String normalizePath(String value) {
        return value == null ? "" : value.strip();
    }

    private void validateHeadingContexts(
            List<String> headingContexts
    ) {
        for (String headingContext : headingContexts) {
            Objects.requireNonNull(
                    headingContext,
                    "headingContexts에는 null이 들어갈 수 없습니다."
            );
        }
    }

    private void validateTableBlock(
            UUID documentId,
            UUID sectionId,
            DisclosureContentBlock tableBlock
    ) {
        Objects.requireNonNull(
                tableBlock,
                "tableBlock은 필수입니다."
        );

        UUID blockId = Objects.requireNonNull(
                tableBlock.getId(),
                "저장되지 않은 Block은 청크로 만들 수 없습니다."
        );

        if (tableBlock.getBlockType()
                != DisclosureContentBlockType.TABLE) {
            throw new IllegalArgumentException(
                    "TABLE Block만 처리할 수 있습니다."
                            + " blockId=" + blockId
            );
        }

        DisclosureDocument actualDocument =
                Objects.requireNonNull(
                        tableBlock.getDisclosureDocument(),
                        "Block의 DisclosureDocument는 필수입니다."
                );

        UUID actualDocumentId =
                Objects.requireNonNull(
                        actualDocument.getId(),
                        "Block의 DisclosureDocument가 저장되지 않았습니다."
                );

        if (!documentId.equals(actualDocumentId)) {
            throw new IllegalArgumentException(
                    "다른 문서의 TABLE Block입니다."
                            + " blockId=" + blockId
            );
        }

        DisclosureSection actualSection = tableBlock.getSection();

        UUID actualSectionId = actualSection == null
                ? null
                : Objects.requireNonNull(
                actualSection.getId(),
                "저장되지 않은 Section을 참조합니다."
        );

        if (!Objects.equals(
                sectionId,
                actualSectionId
        )) {
            throw new IllegalArgumentException(
                    "다른 Section의 TABLE Block입니다."
                            + " blockId=" + blockId
            );
        }

        if (tableBlock.getSequenceNo() < 1) {
            throw new IllegalArgumentException(
                    "Block sequenceNo는 1 이상이어야 합니다."
                            + " blockId=" + blockId
            );
        }
    }


    /**
     * 표 분할 과정에서만 사용하는 임시 모델
     */
    private record TableChunkPart(
            String bodyText,
            List<SerializedTableRow> headerRows,
            List<SerializedTableRow> bodyRows
    ) {

        private TableChunkPart {
            if (bodyText == null || bodyText.isBlank()) {
                throw new IllegalArgumentException(
                        "TABLE 청크 본문은 비어 있을 수 없습니다."
                );
            }

            bodyText = bodyText.strip();

            headerRows = List.copyOf(
                    Objects.requireNonNull(
                            headerRows,
                            "headerRows는 필수입니다."
                    )
            );

            bodyRows = List.copyOf(
                    Objects.requireNonNull(
                            bodyRows,
                            "bodyRows는 필수입니다."
                    )
            );
        }
    }

    /**
     * 하나의 TABLE 블록에서 만들어지는 청크의 순번을 관리
     */
    private static final class PartIndexCounter {

        private int value;

        private int next() {
            return value++;
        }
    }
}
