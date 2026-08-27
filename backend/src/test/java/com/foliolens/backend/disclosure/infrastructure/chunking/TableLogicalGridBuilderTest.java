package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableLogicalGridBuilderTest {

    private final TableLogicalGridBuilder builder =
            new TableLogicalGridBuilder();

    @Test
    void buildsRectangularGridWithoutSpans() {
        ParsedDisclosureTableCell firstHeader = cell(
                0,
                ParsedDisclosureTableCellType.HEADER,
                1,
                1,
                "구분"
        );
        ParsedDisclosureTableCell secondHeader = cell(
                1,
                ParsedDisclosureTableCellType.HEADER,
                1,
                1,
                "금액"
        );
        ParsedDisclosureTableCell firstData = cell(
                0,
                ParsedDisclosureTableCellType.DATA,
                1,
                1,
                "시설투자"
        );
        ParsedDisclosureTableCell secondData = cell(
                1,
                ParsedDisclosureTableCellType.DATA,
                1,
                1,
                "5,000억원"
        );

        ParsedDisclosureTable table = table(
                row(0, firstHeader, secondHeader),
                row(1, firstData, secondData)
        );

        LogicalTableGrid result = builder.build(table);

        assertEquals(2, result.rowCount());
        assertEquals(2, result.columnCount());
        assertEquals("구분", result.rows().get(0).cells().get(0).text());
        assertEquals("금액", result.rows().get(0).cells().get(1).text());
        assertEquals("시설투자", result.rows().get(1).cells().get(0).text());
        assertEquals("5,000억원", result.rows().get(1).cells().get(1).text());
        assertTrue(
                result.rows().stream()
                        .flatMap(logicalRow -> logicalRow.cells().stream())
                        .allMatch(LogicalTableCell::isOrigin)
        );
    }

    @Test
    void expandsCombinedRowSpanAndColSpanWithOriginInformation() {
        ParsedDisclosureTableCell category = cell(
                0,
                ParsedDisclosureTableCellType.HEADER,
                2,
                1,
                "구분"
        );
        ParsedDisclosureTableCell year = cell(
                1,
                ParsedDisclosureTableCellType.HEADER,
                1,
                2,
                "2025년"
        );
        ParsedDisclosureTableCell revenue = cell(
                0,
                ParsedDisclosureTableCellType.HEADER,
                1,
                1,
                "매출"
        );
        ParsedDisclosureTableCell profit = cell(
                1,
                ParsedDisclosureTableCellType.HEADER,
                1,
                1,
                "영업이익"
        );

        LogicalTableGrid result = builder.build(
                table(
                        row(0, category, year),
                        row(1, revenue, profit)
                )
        );

        assertEquals(3, result.columnCount());

        LogicalTableCell categoryOrigin =
                result.rows().get(0).cells().get(0);
        LogicalTableCell yearOrigin =
                result.rows().get(0).cells().get(1);
        LogicalTableCell yearContinuation =
                result.rows().get(0).cells().get(2);
        LogicalTableCell categoryContinuation =
                result.rows().get(1).cells().get(0);

        assertTrue(categoryOrigin.isOrigin());
        assertTrue(yearOrigin.isOrigin());
        assertFalse(yearContinuation.isOrigin());
        assertTrue(yearContinuation.colSpanContinuation());
        assertFalse(yearContinuation.rowSpanContinuation());
        assertSame(year, yearContinuation.sourceCell());
        assertEquals(0, yearContinuation.originRowIndex());
        assertEquals(1, yearContinuation.originColumnIndex());

        assertFalse(categoryContinuation.isOrigin());
        assertTrue(categoryContinuation.rowSpanContinuation());
        assertFalse(categoryContinuation.colSpanContinuation());
        assertSame(category, categoryContinuation.sourceCell());
        assertEquals("매출", result.rows().get(1).cells().get(1).text());
        assertEquals("영업이익", result.rows().get(1).cells().get(2).text());
    }

    @Test
    void movesColSpanCellPastOccupiedRowSpanColumn() {
        ParsedDisclosureTableCell firstColumn = cell(
                0,
                ParsedDisclosureTableCellType.DATA,
                1,
                1,
                "첫째"
        );
        ParsedDisclosureTableCell middleRowSpan = cell(
                1,
                ParsedDisclosureTableCellType.DATA,
                2,
                1,
                "세로 병합"
        );
        ParsedDisclosureTableCell wideCell = cell(
                0,
                ParsedDisclosureTableCellType.DATA,
                1,
                2,
                "가로 병합"
        );

        LogicalTableGrid result = builder.build(
                table(
                        row(0, firstColumn, middleRowSpan),
                        row(1, wideCell)
                )
        );

        assertEquals(4, result.columnCount());
        assertTrue(result.rows().get(1).cells().get(0).isEmpty());
        assertEquals("세로 병합", result.rows().get(1).cells().get(1).text());
        assertEquals("가로 병합", result.rows().get(1).cells().get(2).text());
        assertEquals("가로 병합", result.rows().get(1).cells().get(3).text());
        assertTrue(
                result.rows().get(1).cells().get(3)
                        .colSpanContinuation()
        );
    }

    @Test
    void padsShortRowsWithEmptyLogicalCells() {
        LogicalTableGrid result = builder.build(
                table(
                        row(
                                0,
                                cell(0, ParsedDisclosureTableCellType.DATA,
                                        1, 1, "A"),
                                cell(1, ParsedDisclosureTableCellType.DATA,
                                        1, 1, "B"),
                                cell(2, ParsedDisclosureTableCellType.DATA,
                                        1, 1, "C")
                        ),
                        row(
                                1,
                                cell(0, ParsedDisclosureTableCellType.DATA,
                                        1, 1, "D"),
                                cell(1, ParsedDisclosureTableCellType.DATA,
                                        1, 1, "E")
                        )
                )
        );

        LogicalTableCell padding =
                result.rows().get(1).cells().get(2);

        assertEquals(3, result.columnCount());
        assertTrue(padding.isEmpty());
        assertEquals(-1, padding.originRowIndex());
        assertEquals(-1, padding.originColumnIndex());
    }

    @Test
    void keepsNestedTableOnOriginalSourceCell() {
        ParsedDisclosureTable nestedTable = new ParsedDisclosureTable(
                2,
                30,
                35,
                List.of()
        );
        ParsedDisclosureTableCell cellWithNestedTable =
                new ParsedDisclosureTableCell(
                        0,
                        ParsedDisclosureTableCellType.DATA,
                        1,
                        1,
                        "상위 셀 문맥",
                        11,
                        15,
                        List.of(nestedTable),
                        List.of()
                );

        LogicalTableCell result = builder.build(
                        table(row(0, cellWithNestedTable))
                )
                .rows().getFirst()
                .cells().getFirst();

        assertTrue(result.hasNestedTables());
        assertSame(cellWithNestedTable, result.sourceCell());
        assertSame(
                nestedTable,
                result.sourceCell().nestedTables().getFirst()
        );
    }

    @Test
    void rejectsUnorderedRowsAndCellsOrExcessiveColumnSpan() {
        ParsedDisclosureTable unorderedRows = table(
                row(1, cell(0, ParsedDisclosureTableCellType.DATA,
                        1, 1, "A")),
                row(0, cell(0, ParsedDisclosureTableCellType.DATA,
                        1, 1, "B"))
        );
        ParsedDisclosureTable unorderedCells = table(
                row(
                        0,
                        cell(1, ParsedDisclosureTableCellType.DATA,
                                1, 1, "A"),
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 1, "B")
                )
        );
        ParsedDisclosureTable excessiveSpan = table(
                row(
                        0,
                        cell(0, ParsedDisclosureTableCellType.DATA,
                                1, 10_001, "너무 넓은 셀")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(unorderedRows)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(unorderedCells)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(excessiveSpan)
        );
        assertThrows(
                NullPointerException.class,
                () -> builder.build(null)
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
