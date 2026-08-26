package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LogicalTableGrid를 검색 가능한 행 문자열로 변환한다.
 *
 * 책임:
 * - 셀 내부 공백과 줄바꿈 정규화
 * - 행의 열 위치 보존
 * - rowspan 문맥 반복
 * - colspan 중복 제거
 * - HEADER 행 판별
 * - 빈 행 제거
 *
 * 하지 않는 일:
 * - 청크 크기 분할
 * - Section 경로 추가
 * - 중첩 표 재귀 탐색
 * - DB 저장
 */
@Component
public class TableTextSerializer {

    private static final String INTERNAL_LINE_SEPARATOR = " / ";

    private final ChunkTextNormalizer textNormalizer;

    public TableTextSerializer(ChunkTextNormalizer textNormalizer) {
        this.textNormalizer = Objects.requireNonNull(
                textNormalizer,
                "textNormalizer는 필수입니다."
        );
    }

    public SerializedTable serialize(LogicalTableGrid grid) {
        Objects.requireNonNull(
                grid,
                "grid는 필수입니다."
        );

        List<SerializedTableRow> serializedRows = new ArrayList<>(grid.rowCount());

        for (LogicalTableRow row : grid.rows()) {
            SerializedTableRow serializedRow = serializeRow(row);

            /*
             * 내용 없는 표 행은 검색 가치가 없으므로
             * 최종 SerializedTable에서는 제외한다.
             *
             * rowIndex는 그대로 유지하므로 원본 행 위치는 잃지 않는다.
             */
            if (!serializedRow.isEmpty()) {
                serializedRows.add(serializedRow);
            }
        }

        return new SerializedTable(
                grid.tableOrder(),
                grid.sourceLineStart(),
                grid.sourceLineEnd(),
                grid.columnCount(),
                serializedRows
        );
    }

    private SerializedTableRow serializeRow(LogicalTableRow row) {
        List<String> columnTexts = new ArrayList<>(row.columnCount());

        boolean hasHeaderCell = false;
        boolean hasDataCell = false;

        for (LogicalTableCell cell : row.cells()) {
            String serializedCell = serializeCell(cell);

            columnTexts.add(serializedCell);

            /*
             * 실제 검색 문자열에 포함된 셀만
             * HEADER·DATA 판별에 사용한다.
             */
            if (serializedCell.isBlank()) {
                continue;
            }

            if (cell.type() == ParsedDisclosureTableCellType.HEADER) {
                hasHeaderCell = true;
            } else if (cell.type() == ParsedDisclosureTableCellType.DATA) {
                hasDataCell = true;
            }
        }

        boolean headerOnly = hasHeaderCell && !hasDataCell;

        return new SerializedTableRow(
                row.rowIndex(),
                row.sourceLineStart(),
                row.sourceLineEnd(),
                columnTexts,
                hasHeaderCell,
                headerOnly
        );
    }

    private String serializeCell(LogicalTableCell cell) {
        if (cell.isEmpty()) {
            return "";
        }

        /*
         * colspan으로 오른쪽에 확장된 논리 칸은
         * 같은 텍스트를 중복 출력하지 않는다.
         *
         * 예:
         * "2025년", colSpan=2
         *
         * LogicalGrid:
         * 2025년 | 2025년
         *
         * 직렬화:
         * 2025년 | 빈 칸
         */
        if (cell.colSpanContinuation()) {
            return "";
        }

        /*
         * rowspan으로 아래 행에 이어진 셀은 생략하지 않는다.
         *
         * 예:
         * 구분 | 국내 | 100억원
         * 구분 | 해외 | 200억원
         *
         * 각 행을 독립적으로 검색해도 문맥을 이해할 수 있게 한다.
         */
        String normalized = textNormalizer.normalizeParagraph(cell.text());

        if (normalized.isBlank()) {
            return "";
        }

        /*
         * 한 행을 한 줄로 표현하기 위해
         * 셀 내부 줄바꿈을 명시적인 구분자로 바꾼다.
         *
         * "첫째 줄\n둘째 줄"
         * → "첫째 줄 / 둘째 줄"
         */
        return String.join(
                INTERNAL_LINE_SEPARATOR,
                normalized.lines().toList()
        );
    }
}