package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableContext;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableChunkGeneratorTest {

    private static final UUID DOCUMENT_ID = new UUID(100, 1);
    private static final UUID SECTION_ID = new UUID(200, 1);
    private static final int BLOCK_SEQUENCE_NO = 15;

    private DisclosureDocument document;
    private DisclosureSection section;
    private DisclosureContentBlock tableBlock;
    private DisclosureTablePayloadReader payloadReader;

    @BeforeEach
    void setUp() {
        document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);

        section = mock(DisclosureSection.class);
        when(section.getId()).thenReturn(SECTION_ID);

        tableBlock = mock(DisclosureContentBlock.class);
        when(tableBlock.getId()).thenReturn(new UUID(300, 1));
        when(tableBlock.getDisclosureDocument()).thenReturn(document);
        when(tableBlock.getSection()).thenReturn(section);
        when(tableBlock.getBlockType()).thenReturn(DisclosureContentBlockType.TABLE);
        when(tableBlock.getSequenceNo()).thenReturn(BLOCK_SEQUENCE_NO);
        when(tableBlock.getSourceLineStart()).thenReturn(100);
        when(tableBlock.getSourceLineEnd()).thenReturn(200);

        payloadReader = mock(DisclosureTablePayloadReader.class);
    }

    @Test
    void generatesTableDraftWithSearchContextAndSeparateHeaderSource() {
        ParsedDisclosureTable table = table(
                row(0, 110, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        "구분"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.HEADER,
                        "금액"
                )),
                row(1, 120, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "시설투자"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.DATA,
                        "5,000억원"
                ))
        );
        when(payloadReader.read(tableBlock)).thenReturn(table);

        GeneratedChunkDraft result = generator(
                DisclosureChunkingPolicy.dartXmlV1()
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "II. 사업의 내용 > 시설투자",
                List.of("투자내역"),
                tableBlock
        ).getFirst();

        assertAll(
                () -> assertEquals(DisclosureChunkType.TABLE, result.chunkType()),
                () -> assertEquals(BLOCK_SEQUENCE_NO, result.anchorBlockSequenceNo()),
                () -> assertEquals(0, result.anchorPartIndex()),
                () -> assertEquals(
                        "구분 | 금액\n시설투자 | 5,000억원",
                        result.bodyText()
                ),
                () -> assertTrue(
                        result.searchText().contains("II. 사업의 내용 > 시설투자")
                ),
                () -> assertTrue(result.searchText().contains("투자내역")),
                () -> assertEquals(2, result.sources().size()),
                () -> assertEquals(0, result.sources().get(0).tableRowIndexStart()),
                () -> assertEquals(0, result.sources().get(0).tableRowIndexEnd()),
                () -> assertEquals(110, result.sources().get(0).sourceLineStart()),
                () -> assertEquals(1, result.sources().get(1).tableRowIndexStart()),
                () -> assertEquals(1, result.sources().get(1).tableRowIndexEnd()),
                () -> assertEquals(120, result.sources().get(1).sourceLineStart()),
                () -> assertNull(result.sources().getFirst().tableNestingPath())
        );
    }

    @Test
    void splitsLargeTableByRowsAndRepeatsLeadingHeader() {
        ParsedDisclosureTable table = table(
                row(0, 110, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        "항목"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.HEADER,
                        "금액"
                )),
                row(1, 120, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "매출액"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.DATA,
                        "100억원"
                )),
                row(2, 130, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "영업이익"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.DATA,
                        "20억원"
                ))
        );
        when(payloadReader.read(tableBlock)).thenReturn(table);

        List<GeneratedChunkDraft> result = generator(
                policyWithTableSizes(10, 20, 30, 60)
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "재무정보",
                List.of(),
                tableBlock
        );

        assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.get(0).bodyText().startsWith("항목 | 금액\n")),
                () -> assertTrue(result.get(1).bodyText().startsWith("항목 | 금액\n")),
                () -> assertTrue(result.get(0).bodyText().contains("매출액 | 100억원")),
                () -> assertTrue(result.get(1).bodyText().contains("영업이익 | 20억원")),
                () -> assertEquals(0, result.get(0).anchorPartIndex()),
                () -> assertEquals(1, result.get(1).anchorPartIndex()),
                () -> assertEquals(2, result.get(0).sources().size()),
                () -> assertEquals(2, result.get(1).sources().size()),
                () -> assertEquals(1, result.get(0).sources().get(1).tableRowIndexStart()),
                () -> assertEquals(2, result.get(1).sources().get(1).tableRowIndexStart())
        );
    }

    @Test
    void splitsSingleOversizedRowWithinAbsoluteMaximum() {
        String longCellText = "시설투자 목적과 세부 집행계획을 설명하는 문장입니다. ".repeat(8);
        ParsedDisclosureTable table = table(
                row(0, 110, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        longCellText
                ))
        );
        when(payloadReader.read(tableBlock)).thenReturn(table);

        int absoluteMaxChars = 60;
        List<GeneratedChunkDraft> result = generator(
                policyWithTableSizes(10, 25, 40, absoluteMaxChars)
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "투자내역",
                List.of(),
                tableBlock
        );

        assertAll(
                () -> assertTrue(result.size() > 1),
                () -> assertTrue(
                        result.stream()
                                .allMatch(draft -> draft.bodyText().length()
                                        <= absoluteMaxChars)
                ),
                () -> assertTrue(
                        result.stream()
                                .allMatch(draft -> draft.sources().size() == 1)
                ),
                () -> assertTrue(
                        result.stream()
                                .allMatch(draft -> draft.sources().getFirst()
                                        .tableRowIndexStart() == 0)
                ),
                () -> assertEquals(
                        List.of(0, 1, 2, 3, 4, 5, 6, 7),
                        result.stream()
                                .map(GeneratedChunkDraft::anchorPartIndex)
                                .toList()
                )
        );
    }

    @Test
    void treatsOversizedLeadingHeadersAsOrdinaryRowsWithoutLosingContent() {
        String firstHeader = "첫 번째로 매우 긴 표 머리글 설명입니다. ".repeat(2);
        String secondHeader = "두 번째로 매우 긴 표 머리글 설명입니다. ".repeat(2);

        ParsedDisclosureTable table = table(
                row(0, 110, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        firstHeader
                )),
                row(1, 120, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        secondHeader
                )),
                row(2, 130, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "본문 값"
                ))
        );
        when(payloadReader.read(tableBlock)).thenReturn(table);

        int absoluteMaxChars = 60;
        List<GeneratedChunkDraft> result = generator(
                policyWithTableSizes(10, 20, 30, absoluteMaxChars)
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "재무정보",
                List.of(),
                tableBlock
        );

        String combinedBodyText = result.stream()
                .map(GeneratedChunkDraft::bodyText)
                .reduce("", (left, right) -> left + "\n" + right);

        assertAll(
                () -> assertTrue(result.size() > 1),
                () -> assertTrue(
                        result.stream().allMatch(draft ->
                                draft.bodyText().length() <= absoluteMaxChars
                        )
                ),
                () -> assertTrue(combinedBodyText.contains(firstHeader.strip())),
                () -> assertTrue(combinedBodyText.contains(secondHeader.strip())),
                () -> assertTrue(combinedBodyText.contains("본문 값")),
                () -> assertTrue(
                        result.stream().allMatch(draft ->
                                draft.sources().size() == 1
                        )
                )
        );
    }

    @Test
    void generatesNestedTableAsSeparateDraftWithPathAndParentContext() {
        ParsedDisclosureTable nestedTable = table(
                row(0, 210, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        "구분"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.HEADER,
                        "금액"
                )),
                row(1, 220, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "국내"
                ), cell(
                        1,
                        ParsedDisclosureTableCellType.DATA,
                        "100억원"
                ))
        );
        ParsedDisclosureTableCell parentCell = new ParsedDisclosureTableCell(
                0,
                ParsedDisclosureTableCellType.DATA,
                1,
                1,
                "사업부문별 실적",
                111,
                119,
                List.of(nestedTable),
                List.of()
        );
        ParsedDisclosureTable rootTable = table(row(0, 110, parentCell));
        when(payloadReader.read(tableBlock)).thenReturn(rootTable);

        List<GeneratedChunkDraft> result = generator(
                DisclosureChunkingPolicy.dartXmlV1()
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "III. 재무에 관한 사항",
                List.of("요약 재무정보"),
                tableBlock
        );

        GeneratedChunkDraft nestedDraft = result.get(1);

        assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertEquals("사업부문별 실적", result.getFirst().bodyText()),
                () -> assertEquals(1, nestedDraft.anchorPartIndex()),
                () -> assertTrue(
                        nestedDraft.searchText().contains(
                                "상위 셀 문맥: 사업부문별 실적"
                        )
                ),
                () -> assertEquals(
                        "rows[0].cells[0].nestedTables[0]",
                        nestedDraft.sources().getFirst().tableNestingPath()
                ),
                () -> assertEquals(0, nestedDraft.sources().getFirst().tableRowIndexStart()),
                () -> assertEquals(0, nestedDraft.sources().getFirst().tableRowIndexEnd()),
                () -> assertEquals(1, nestedDraft.sources().get(1).tableRowIndexStart()),
                () -> assertEquals(1, nestedDraft.sources().get(1).tableRowIndexEnd())
        );
    }

    @Test
    void limitsLongParentCellContextForNestedTableSearchText() {
        ParsedDisclosureTable nestedTable = table(
                row(0, 210, cell(
                        0,
                        ParsedDisclosureTableCellType.HEADER,
                        "구분"
                )),
                row(1, 220, cell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        "변경 내용"
                ))
        );

        String tailMarker = "문맥끝에만있는표식";
        String longParentContext = "중요한 회계정책 설명 "
                + "부모 셀의 긴 설명입니다. ".repeat(120)
                + tailMarker;

        ParsedDisclosureTableCell parentCell =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        longParentContext,
                        111,
                        119,
                        List.of(nestedTable),
                        List.of()
                );

        when(payloadReader.read(tableBlock))
                .thenReturn(table(row(0, 110, parentCell)));

        List<GeneratedChunkDraft> result = generator(
                DisclosureChunkingPolicy.dartXmlV2()
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "III. 재무에 관한 사항",
                List.of(),
                tableBlock
        );

        GeneratedChunkDraft nestedDraft = result.stream()
                .filter(draft -> draft.sources().stream()
                        .anyMatch(source ->
                                source.tableNestingPath() != null
                        ))
                .findFirst()
                .orElseThrow();

        String contextLine = nestedDraft.searchText()
                .lines()
                .filter(line -> line.startsWith("상위 셀 문맥: "))
                .findFirst()
                .orElseThrow();

        String context = contextLine.substring(
                "상위 셀 문맥: ".length()
        );

        assertAll(
                () -> assertTrue(context.length() <= 500),
                () -> assertTrue(context.startsWith("중요한 회계정책 설명")),
                () -> assertTrue(context.endsWith("…")),
                () -> assertTrue(!context.contains(tailMarker)),
                () -> assertTrue(
                        nestedDraft.searchText().contains("변경 내용")
                )
        );
    }

    @Test
    void usesVersionTwoAdjacentContextInsteadOfWholeParentCellText() {
        ParsedDisclosureTable nestedTable = new ParsedDisclosureTable(
                2,
                210,
                230,
                new ParsedDisclosureTableContext(
                        "표 바로 앞 핵심 설명입니다.",
                        "표 바로 뒤 보충 설명입니다."
                ),
                List.of(
                        row(0, 211, cell(
                                0,
                                ParsedDisclosureTableCellType.HEADER,
                                "구분"
                        )),
                        row(1, 220, cell(
                                0,
                                ParsedDisclosureTableCellType.DATA,
                                "100억원"
                        ))
                )
        );

        ParsedDisclosureTableCell parentCell =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        "관련 없는 먼 설명입니다. 표 바로 앞 핵심 설명입니다. "
                                + "표 바로 뒤 보충 설명입니다. 또 다른 먼 설명입니다.",
                        111,
                        119,
                        List.of(nestedTable),
                        List.of()
                );

        when(payloadReader.read(tableBlock))
                .thenReturn(table(row(0, 110, parentCell)));

        GeneratedChunkDraft nestedDraft = generator(
                DisclosureChunkingPolicy.dartXmlV3()
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "III. 재무에 관한 사항",
                List.of(),
                tableBlock
        ).get(1);

        assertAll(
                () -> assertTrue(
                        nestedDraft.searchText().contains(
                                "표 바로 앞 핵심 설명입니다. "
                                        + "표 바로 뒤 보충 설명입니다."
                        )
                ),
                () -> assertFalse(
                        nestedDraft.searchText().contains(
                                "관련 없는 먼 설명"
                        )
                ),
                () -> assertFalse(
                        nestedDraft.searchText().contains(
                                "또 다른 먼 설명"
                        )
                ),
                () -> assertTrue(
                        nestedDraft.searchText().contains("100억원")
                )
        );
    }

    @Test
    void prioritizesNearestContextAcrossMultipleNestedLevels() {
        String farAncestorMarker = "가장먼조상표식";
        String nearestAncestorMarker = "가까운조상표식";
        String directMarker = "가장가까운직접부모표식";

        ParsedDisclosureTable innermostTable =
                new ParsedDisclosureTable(
                        3,
                        310,
                        330,
                        new ParsedDisclosureTableContext(
                                directMarker + " 문장입니다.",
                                null
                        ),
                        List.of(row(0, 311, cell(
                                0,
                                ParsedDisclosureTableCellType.DATA,
                                "최하위 표 값"
                        )))
                );

        ParsedDisclosureTableCell middleCell =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        "중간 표 셀",
                        211,
                        299,
                        List.of(innermostTable),
                        List.of()
                );

        ParsedDisclosureTable middleTable =
                new ParsedDisclosureTable(
                        2,
                        210,
                        300,
                        new ParsedDisclosureTableContext(
                                farAncestorMarker
                                        + " 문장입니다. "
                                        + "조상 문맥입니다. ".repeat(50)
                                        + nearestAncestorMarker
                                        + " 문장입니다.",
                                null
                        ),
                        List.of(row(0, 211, middleCell))
                );

        ParsedDisclosureTableCell rootCell =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        "최상위 표 셀",
                        111,
                        199,
                        List.of(middleTable),
                        List.of()
                );

        when(payloadReader.read(tableBlock))
                .thenReturn(table(row(0, 110, rootCell)));

        GeneratedChunkDraft innermostDraft = generator(
                DisclosureChunkingPolicy.dartXmlV3()
        ).generate(
                DOCUMENT_ID,
                SECTION_ID,
                "III. 재무에 관한 사항",
                List.of(),
                tableBlock
        ).getLast();

        String contextLine = innermostDraft.searchText()
                .lines()
                .filter(line -> line.startsWith("상위 셀 문맥: "))
                .findFirst()
                .orElseThrow();
        String context = contextLine.substring(
                "상위 셀 문맥: ".length()
        );

        assertAll(
                () -> assertTrue(context.length() <= 500),
                () -> assertTrue(context.contains(directMarker)),
                () -> assertTrue(context.contains(nearestAncestorMarker)),
                () -> assertFalse(context.contains(farAncestorMarker)),
                () -> assertTrue(
                        innermostDraft.searchText().contains("최하위 표 값")
                )
        );
    }

    @Test
    void rejectsBlockFromAnotherDocumentOrNonTableBlock() {
        DisclosureDocument anotherDocument = mock(DisclosureDocument.class);
        when(anotherDocument.getId()).thenReturn(new UUID(100, 2));

        DisclosureContentBlock foreignBlock = tableBlock(
                anotherDocument,
                section,
                DisclosureContentBlockType.TABLE
        );
        DisclosureContentBlock paragraphBlock = tableBlock(
                document,
                section,
                DisclosureContentBlockType.PARAGRAPH
        );

        TableChunkGenerator generator = generator(
                DisclosureChunkingPolicy.dartXmlV1()
        );

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> generator.generate(
                                DOCUMENT_ID,
                                SECTION_ID,
                                "",
                                List.of(),
                                foreignBlock
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> generator.generate(
                                DOCUMENT_ID,
                                SECTION_ID,
                                "",
                                List.of(),
                                paragraphBlock
                        )
                )
        );
    }

    private TableChunkGenerator generator(DisclosureChunkingPolicy policy) {
        ChunkTextNormalizer normalizer = new ChunkTextNormalizer();

        return new TableChunkGenerator(
                policy,
                payloadReader,
                new NestedTableContextSelector(
                        normalizer,
                        new SentenceBoundarySplitter()
                ),
                new TableLogicalGridBuilder(),
                new TableTextSerializer(normalizer),
                normalizer,
                new SentenceBoundarySplitter()
        );
    }

    private DisclosureChunkingPolicy policyWithTableSizes(
            int targetMin,
            int targetMax,
            int normalMax,
            int absoluteMax
    ) {
        return new DisclosureChunkingPolicy(
                "test-generator",
                "test-v1",
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        10,
                        20,
                        30,
                        60
                ),
                new DisclosureChunkingPolicy.ChunkSizePolicy(
                        targetMin,
                        targetMax,
                        normalMax,
                        absoluteMax
                )
        );
    }

    private ParsedDisclosureTable table(ParsedDisclosureTableRow... rows) {
        return new ParsedDisclosureTable(
                1,
                100,
                300,
                List.of(rows)
        );
    }

    private ParsedDisclosureTableRow row(
            int rowIndex,
            int sourceLine,
            ParsedDisclosureTableCell... cells
    ) {
        return new ParsedDisclosureTableRow(
                rowIndex,
                sourceLine,
                sourceLine,
                List.of(cells)
        );
    }

    private ParsedDisclosureTableCell cell(
            int cellIndex,
            ParsedDisclosureTableCellType type,
            String text
    ) {
        return new ParsedDisclosureTableCell(
                cellIndex,
                type,
                1,
                1,
                text,
                1,
                1,
                List.of(),
                List.of()
        );
    }

    private DisclosureContentBlock tableBlock(
            DisclosureDocument ownerDocument,
            DisclosureSection ownerSection,
            DisclosureContentBlockType blockType
    ) {
        DisclosureContentBlock result = mock(DisclosureContentBlock.class);
        when(result.getId()).thenReturn(UUID.randomUUID());
        when(result.getDisclosureDocument()).thenReturn(ownerDocument);
        when(result.getSection()).thenReturn(ownerSection);
        when(result.getBlockType()).thenReturn(blockType);
        when(result.getSequenceNo()).thenReturn(BLOCK_SEQUENCE_NO);
        return result;
    }
}
