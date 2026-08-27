package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 파싱된 표의 rowSpan과 colSpan을 펼쳐
 * 모든 행이 동일한 열 위치를 가지는 논리적 격자를 만듦
 *
 * 원본 ParsedDisclosureTable은 변경하지 않는다.
 */
@Component
public class TableLogicalGridBuilder {

    /**
     * 비정상적으로 큰 colspan 때문에 메모리가 과도하게 사용되는 것을 방지하기 위한 안전장치
     * 실제 데이터 검증 결과에 따라 조정할 수 있다.
     */
    private static final int MAX_LOGICAL_COLUMNS = 10_000;


    /**
     * 작업 순서:
     * 입력 표가 null인지 확인
     * 행과 셀 인덱스 순서 확인
     * 행을 첫 번째부터 순회
     * 만료된 rowSpan 제거
     * 이전 행에서 내려온 셀 배치
     * 현재 행의 셀 배치
     * 표의 최대 열 개수 계산
     * 최종 LogicalTableGrid 생성
     */
    public LogicalTableGrid build(ParsedDisclosureTable table) {
        Objects.requireNonNull(
                table,
                "table은 필수입니다."
        );

        // 파서 결과의 행과 셀이 원본 순서대로 들어 있는지 검사
        validateSourceOrder(table);

        List<MutableLogicalRow> mutableRows = new ArrayList<>(table.rows().size());

        /*
         * key: 논리적 columnIndex
         * value: 이전 행에서 시작되어 현재 행까지 내려오는 rowspan
         */
        Map<Integer, ActiveRowSpan> activeRowSpans = new HashMap<>();

        int maximumColumnCount = 0;

        for (
                int rowPosition = 0;
                rowPosition < table.rows().size();
                rowPosition++
        ) {
            ParsedDisclosureTableRow sourceRow = table.rows().get(rowPosition);

            // 현재 행에서는 더 이상 사용되지 않는 rowSpan 정보를 제거
            removeExpiredRowSpans(activeRowSpans, rowPosition);

            // 현재 행에서 이미 사용되고 있는 논리적 열을 저장
            Map<Integer, LogicalTableCell> occupied =
                    // 이전 행에서 시작된 rowSpan 셀을 현재 행에 먼저 배치
                    createRowSpanCells(
                            sourceRow,
                            rowPosition,
                            activeRowSpans
                    );

            // 현재 행의 새로운 셀을 어느 열부터 배치할지 나타냄
            int columnCursor = 0;

            for (ParsedDisclosureTableCell sourceCell : sourceRow.cells()) {

                /*
                 * rowspan 셀이 이미 차지한 열을 피하면서
                 * colspan만큼 연속으로 비어 있는 위치를 찾는다.
                 */
                int startColumn = findNextFreeRange(
                        occupied,
                        columnCursor,
                        sourceCell.colSpan()
                );

                // 현재 원본 셀을 논리적 격자에 실제로 배치
                // 1. colSpan 확장
                // 2. rowSpan 등록
                placeCell(
                        sourceRow,
                        sourceCell,
                        rowPosition,
                        startColumn,
                        occupied,
                        activeRowSpans
                );

                columnCursor = startColumn + sourceCell.colSpan();
            }

            int rowColumnCount = occupied.keySet().stream()
                    .max(Comparator.naturalOrder())
                    .map(maximumIndex -> maximumIndex + 1)
                    .orElse(0);

            maximumColumnCount = Math.max(
                    maximumColumnCount,
                    rowColumnCount
            );

            mutableRows.add(
                    new MutableLogicalRow(
                            sourceRow,
                            Map.copyOf(occupied)
                    )
            );
        }

        // 행마다 서로 다른 열 개수를 최종 최대 열 개수에 맞춤
        return completeGrid(
                table,
                mutableRows,
                maximumColumnCount
        );
    }

    /**
     * 이전 행에서 시작된 rowspan을 현재 행에 배치
     *
     * 예를 들어 이전 행의 구분 셀이 0열을 차지하고 있다면:
     *  현재 행 초기 상태:
     *      0열: 구분
     *      1열: 비어 있음
     *      2열: 비어 있음
     *  이때 생성되는 논리 셀은 다음 상태:
     *      sourceCell = 구분 원본 셀
     *      rowSpanContinuation = true
     */
    private Map<Integer, LogicalTableCell> createRowSpanCells(
            ParsedDisclosureTableRow sourceRow,
            int rowPosition,
            Map<Integer, ActiveRowSpan> activeRowSpans
    ) {
        Map<Integer, LogicalTableCell> result = new HashMap<>();

        for (Map.Entry<Integer, ActiveRowSpan> entry : activeRowSpans.entrySet()) {

            ActiveRowSpan span = entry.getValue();

            if (rowPosition >= span.endRowPositionExclusive()) {
                continue;
            }

            int columnIndex = entry.getKey();

            result.put(
                    columnIndex,
                    new LogicalTableCell(
                            sourceRow.rowIndex(),
                            columnIndex,
                            span.sourceCell(),
                            span.originRowIndex(),
                            span.originColumnIndex(),
                            true,
                            span.horizontalOffset() > 0
                    )
            );
        }

        return result;
    }

    /**
     * 현재 원본 셀을 논리적 격자에 실제로 배치
     * 1. colSpan 확장
     *  셀의 colSpan=3이면 세 개의 논리 칸을 만듭니다.
     * 2. rowSpan 등록
     *  셀의 rowSpan이 2 이상이면 다음 행에서도 사용할 수 있도록 activeRowSpans에 등록
     */
    private void placeCell(
            ParsedDisclosureTableRow sourceRow,
            ParsedDisclosureTableCell sourceCell,
            int rowPosition,
            int startColumn,
            Map<Integer, LogicalTableCell> occupied,
            Map<Integer, ActiveRowSpan> activeRowSpans
    ) {
        int endRowPositionExclusive =
                // rowSpan 셀이 어느 행 위치까지 유지되는지 계산
                calculateEndRowPosition(
                        rowPosition,
                        sourceCell.rowSpan()
                );

        for (
                int horizontalOffset = 0;
                horizontalOffset < sourceCell.colSpan();
                horizontalOffset++
        ) {
            int columnIndex = startColumn + horizontalOffset;

            if (columnIndex >= MAX_LOGICAL_COLUMNS) {
                throw new IllegalArgumentException(
                        "논리적 표의 열 개수가 안전 한도를 초과했습니다."
                                + " max=" + MAX_LOGICAL_COLUMNS
                );
            }

            LogicalTableCell previous = occupied.put(
                    columnIndex,
                    new LogicalTableCell(
                            sourceRow.rowIndex(),
                            columnIndex,
                            sourceCell,
                            sourceRow.rowIndex(),
                            startColumn,
                            false,
                            horizontalOffset > 0
                    )
            );

            if (previous != null) {
                throw new IllegalStateException(
                        "논리적 표 셀이 같은 위치에 중복 배치되었습니다."
                                + " rowIndex=" + sourceRow.rowIndex()
                                + ", columnIndex=" + columnIndex
                );
            }

            if (sourceCell.rowSpan() > 1) {
                ActiveRowSpan previousSpan =
                        activeRowSpans.put(
                                columnIndex,
                                new ActiveRowSpan(
                                        sourceCell,
                                        sourceRow.rowIndex(),
                                        startColumn,
                                        horizontalOffset,
                                        endRowPositionExclusive
                                )
                        );

                if (previousSpan != null) {
                    throw new IllegalStateException(
                            "rowSpan 셀이 같은 열에 중복 등록되었습니다."
                                    + " columnIndex=" + columnIndex
                    );
                }
            }
        }
    }

    /**
     * colspan 크기만큼 연속으로 비어 있는 첫 위치 찾음
     */
    private int findNextFreeRange(
            Map<Integer, LogicalTableCell> occupied,
            int startColumn,
            int requiredWidth
    ) {
        if (requiredWidth < 1) {
            throw new IllegalArgumentException(
                    "requiredWidth는 1 이상이어야 합니다."
            );
        }

        int candidate = Math.max(0, startColumn);

        while (candidate < MAX_LOGICAL_COLUMNS) {
            if (candidate > MAX_LOGICAL_COLUMNS - requiredWidth) {
                throw new IllegalArgumentException(
                        "논리적 표의 열 개수가 안전 한도를 초과했습니다."
                                + " max=" + MAX_LOGICAL_COLUMNS
                );
            }

            Integer collisionColumn = null;

            for (int offset = 0; offset < requiredWidth; offset++) {
                int columnIndex = candidate + offset;

                if (occupied.containsKey(columnIndex)) {
                    collisionColumn = columnIndex;
                    break;
                }
            }

            if (collisionColumn == null) {
                return candidate;
            }

            candidate = collisionColumn + 1;
        }

        throw new IllegalArgumentException(
                "셀을 배치할 수 있는 논리적 열을 찾지 못했습니다."
        );
    }

    /**
     * 현재 행에서는 더 이상 사용되지 않는 rowSpan 정보를 제거
     * 예를 들어 0행에서 시작한 셀이 rowSpan=2라면:
     * 0행과 1행에서만 유지되고, 2행을 처리하기 전에 제거
     */
    private void removeExpiredRowSpans(
            Map<Integer, ActiveRowSpan> activeRowSpans,
            int rowPosition
    ) {
        activeRowSpans.entrySet().removeIf(
                entry -> rowPosition
                        >= entry.getValue().endRowPositionExclusive()
        );
    }

    /**
     * 모든 행의 열 개수를 표의 최대 열 개수로 맞춘다.
     * 값이 없는 위치는 빈 LogicalTableCell로 채운다.
     *
     * 예:
     * 중간결과 =
     * 0행: A | B | C
     * 1행: D | E
     * 2행: F | G | H
     * 최종결과 =
     * 0행: A | B | C
     * 1행: D | E | 빈 칸
     * 2행: F | G | H
     */
    private LogicalTableGrid completeGrid(
            ParsedDisclosureTable table,
            List<MutableLogicalRow> mutableRows,
            int columnCount
    ) {
        List<LogicalTableRow> completedRows =
                new ArrayList<>(mutableRows.size());

        for (MutableLogicalRow mutableRow : mutableRows) {
            ParsedDisclosureTableRow sourceRow =
                    mutableRow.sourceRow();

            List<LogicalTableCell> cells =
                    new ArrayList<>(columnCount);

            for (
                    int columnIndex = 0;
                    columnIndex < columnCount;
                    columnIndex++
            ) {
                cells.add(
                        mutableRow.cells().getOrDefault(
                                columnIndex,
                                LogicalTableCell.empty(
                                        sourceRow.rowIndex(),
                                        columnIndex
                                )
                        )
                );
            }

            completedRows.add(
                    new LogicalTableRow(
                            sourceRow.rowIndex(),
                            sourceRow.sourceLineStart(),
                            sourceRow.sourceLineEnd(),
                            cells
                    )
            );
        }

        return new LogicalTableGrid(
                table.order(),
                table.sourceLineStart(),
                table.sourceLineEnd(),
                columnCount,
                completedRows
        );
    }

    // rowSpan 셀이 어느 행 위치까지 유지되는지 계산
    // rowPosition = 2
    // rowSpan = 3
    // ->
    // 사용되는 위치: 2, 3, 4
    // 종료 위치: 5
    private int calculateEndRowPosition(
            int rowPosition,
            int rowSpan
    ) {
        long result = (long) rowPosition + rowSpan;

        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "rowSpan 범위가 너무 큽니다."
            );
        }

        return (int) result;
    }

    /**
     * 파서가 만든 행과 셀의 인덱스가 오름차순인지 검증한다.
     */
    private void validateSourceOrder(
            ParsedDisclosureTable table
    ) {
        int previousRowIndex = -1;

        for (ParsedDisclosureTableRow row : table.rows()) {
            Objects.requireNonNull(
                    row,
                    "표의 rows에는 null이 들어갈 수 없습니다."
            );

            if (row.rowIndex() <= previousRowIndex) {
                throw new IllegalArgumentException(
                        "표의 rowIndex는 오름차순이어야 합니다."
                                + " rowIndex=" + row.rowIndex()
                );
            }

            previousRowIndex = row.rowIndex();

            int previousCellIndex = -1;

            for (ParsedDisclosureTableCell cell : row.cells()) {
                Objects.requireNonNull(
                        cell,
                        "행의 cells에는 null이 들어갈 수 없습니다."
                );

                if (cell.cellIndex() <= previousCellIndex) {
                    throw new IllegalArgumentException(
                            "행의 cellIndex는 오름차순이어야 합니다."
                                    + " rowIndex=" + row.rowIndex()
                                    + ", cellIndex=" + cell.cellIndex()
                    );
                }

                previousCellIndex = cell.cellIndex();
            }
        }
    }

    /**
     * 표를 만드는 중간 단계에서 사용하는 임시 모델
     * 아직 전체 표의 최대 열 개수를 모르기 때문에 우선 Map으로 셀을 저장
     *
     * 0열 → A
     * 1열 → B
     * 3열 → C
     * 모든 행을 조사한 후 completeGrid()에서 빈 2열을 채우고 LogicalTableRow로 변환
     */
    private record MutableLogicalRow(
            ParsedDisclosureTableRow sourceRow,
            Map<Integer, LogicalTableCell> cells
    ) {
    }

    /**
     * 다음 행으로 이어지는 rowspan 셀의 상태
     *
     * colSpan이 함께 적용된 셀이라면 열마다 하나씩 저장
     */
    private record ActiveRowSpan(
            ParsedDisclosureTableCell sourceCell, // 원본 TH 또는 TD 셀
            int originRowIndex, // 원본 셀이 시작한 행
            int originColumnIndex, // 원본 셀이 시작한 논리 열
            int horizontalOffset, // colSpan으로 확장된 몇 번째 열인지
            int endRowPositionExclusive // 이 rowspan이 끝나는 행 위치
    ) {
    }
}