package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableTextSerializerTest {

    private final TableLogicalGridBuilder gridBuilder =
            new TableLogicalGridBuilder();

    private final TableTextSerializer serializer =
            new TableTextSerializer(
                    new ChunkTextNormalizer()
            );

    @Test
    void serializesSpansAndDetectsLeadingHeaderRows() {
        ParsedDisclosureTable table = table(
                row(
                        0,
                        cell(0, ParsedDisclosureTableCellType.HEADER,
                                2, 1, "구분"),
                        cell(1, ParsedDisclosureTableCellType.HEADER,
                                1, 2, "2025년")
                ),
                row(
                        1,
                        cell(0, ParsedDisclosureTableCellType.HEADER,
                                1, 1, "매출"),
                        cell(1, ParsedDisclosureTableCellType.HEADER,
                                1, 1, "영업이익")
                ),
                row(
                        2,
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 1, "제품 A"),
                        cell(1, ParsedDisclosureTableCellType.DATA,
                                1, 1, "100억원"),
                        cell(2, ParsedDisclosureTableCellType.DATA,
                                1, 1, "20억원")
                )
        );

        SerializedTable result = serializer.serialize(
                gridBuilder.build(table)
        );

        assertEquals(3, result.columnCount());
        assertEquals(3, result.rows().size());
        assertEquals(
                List.of("구분", "2025년", ""),
                result.rows().get(0).columnTexts()
        );
        assertEquals(
                "구분 | 2025년",
                result.rows().get(0).text()
        );
        assertEquals(
                "구분 | 매출 | 영업이익",
                result.rows().get(1).text()
        );
        assertEquals(
                "제품 A | 100억원 | 20억원",
                result.rows().get(2).text()
        );
        assertTrue(result.rows().get(0).headerOnly());
        assertTrue(result.rows().get(1).headerOnly());
        assertFalse(result.rows().get(2).hasHeaderCell());
        assertFalse(result.rows().get(2).headerOnly());
        assertEquals(2, result.leadingHeaderRows().size());
        assertEquals(
                "구분 | 2025년\n"
                        + "구분 | 매출 | 영업이익\n"
                        + "제품 A | 100억원 | 20억원",
                result.text()
        );
        assertEquals(result.text().length(), result.characterCount());
    }

    @Test
    void normalizesCellLinesAndPreservesInteriorEmptyColumns() {
        ParsedDisclosureTable table = table(
                row(
                        0,
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 1, " 첫째  줄\r\n 둘째\t줄 "),
                        cell(1, ParsedDisclosureTableCellType.DATA,
                                1, 1, null),
                        cell(2, ParsedDisclosureTableCellType.DATA,
                                1, 1, " C "),
                        cell(3, ParsedDisclosureTableCellType.DATA,
                                1, 1, null)
                )
        );

        SerializedTableRow result = serializer.serialize(
                        gridBuilder.build(table)
                )
                .rows().getFirst();

        assertEquals(
                List.of(
                        "첫째 줄 / 둘째 줄",
                        "",
                        "C",
                        ""
                ),
                result.columnTexts()
        );
        assertEquals(
                "첫째 줄 / 둘째 줄 |  | C",
                result.text()
        );
        assertEquals(result.text().length(), result.characterCount());
    }

    @Test
    void removesRowsWithoutSearchableTextAndKeepsOriginalRowIndex() {
        ParsedDisclosureTable table = table(
                row(
                        0,
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 1, null)
                ),
                row(
                        1,
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 1, "남은 행")
                )
        );

        SerializedTable result = serializer.serialize(
                gridBuilder.build(table)
        );

        assertEquals(1, result.rows().size());
        assertEquals(1, result.rows().getFirst().rowIndex());
        assertEquals("남은 행", result.text());
    }

    @Test
    void classifiesMixedHeaderAndDataRowAsBodyRow() {
        ParsedDisclosureTable table = table(
                row(
                        0,
                        cell(0, ParsedDisclosureTableCellType.HEADER,
                                1, 1, "항목"),
                        cell(1, ParsedDisclosureTableCellType.DATA,
                                1, 1, "값")
                )
        );

        SerializedTableRow result = serializer.serialize(
                        gridBuilder.build(table)
                )
                .rows().getFirst();

        assertTrue(result.hasHeaderCell());
        assertFalse(result.headerOnly());
        assertEquals(List.of(), serializer.serialize(
                gridBuilder.build(table)
        ).leadingHeaderRows());
    }

    @Test
    void returnsEmptySerializedTableForEmptyGridAndRejectsNull() {
        LogicalTableGrid emptyGrid = new LogicalTableGrid(
                1,
                1,
                2,
                0,
                List.of()
        );

        SerializedTable result = serializer.serialize(emptyGrid);

        assertTrue(result.isEmpty());
        assertEquals("", result.text());
        assertEquals(0, result.characterCount());
        assertThrows(
                NullPointerException.class,
                () -> serializer.serialize(null)
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
        int sourceLine = 10 + rowIndex;

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
            int rowSpan,
            int colSpan,
            String text
    ) {
        return new ParsedDisclosureTableCell(
                cellIndex,
                type,
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
