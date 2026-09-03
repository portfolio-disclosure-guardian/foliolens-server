package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureImage;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCell;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableCellType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableRow;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * HTML TABLE을 기존 공시 표 파싱 모델로 변환한다.
 * rowspan/colspan은 원본 값 그대로 보존하고 논리 격자 확장은 수행하지 않는다.
 */
@Component
public class HtmlTableParser {

    private final HtmlTextExtractor textExtractor;
    private final HtmlSourceLocationResolver locationResolver;

    public HtmlTableParser(
            HtmlTextExtractor textExtractor,
            HtmlSourceLocationResolver locationResolver
    ) {
        this.textExtractor = Objects.requireNonNull(
                textExtractor,
                "textExtractor는 필수입니다."
        );
        this.locationResolver = Objects.requireNonNull(
                locationResolver,
                "locationResolver는 필수입니다."
        );
    }

    /**
     * 단독 표를 지정된 순번부터 파싱한다. 중첩 표는 다음 순번을 사용한다.
     */
    public ParsedDisclosureTable parse(Element tableElement, int order) {
        if (order < 0) {
            throw new IllegalArgumentException("order는 0 이상이어야 합니다.");
        }
        AtomicInteger orderSequence = new AtomicInteger(order);
        return parse(tableElement, orderSequence::getAndIncrement);
    }

    /**
     * 문서 전체가 공유하는 순번 공급자를 받아 최상위·중첩 표 순서를 보존한다.
     */
    public ParsedDisclosureTable parse(
            Element tableElement,
            IntSupplier orderSupplier
    ) {
        requireTable(tableElement);
        Objects.requireNonNull(orderSupplier, "orderSupplier는 필수입니다.");
        return parseTable(tableElement, orderSupplier);
    }

    private ParsedDisclosureTable parseTable(
            Element tableElement,
            IntSupplier orderSupplier
    ) {
        int tableOrder = orderSupplier.getAsInt();
        if (tableOrder < 0) {
            throw new IllegalArgumentException(
                    "orderSupplier는 0 이상의 값을 반환해야 합니다."
            );
        }

        HtmlSourceLineRange tableRange = locationResolver.resolve(tableElement);
        List<ParsedDisclosureTableRow> parsedRows = new ArrayList<>();
        List<Element> rowElements = immediateRows(tableElement);

        for (int rowIndex = 0; rowIndex < rowElements.size(); rowIndex++) {
            parsedRows.add(parseRow(
                    tableElement,
                    rowElements.get(rowIndex),
                    rowIndex,
                    orderSupplier
            ));
        }

        return new ParsedDisclosureTable(
                tableOrder,
                tableRange.startLine(),
                tableRange.endLine(),
                null,
                parsedRows
        );
    }

    private ParsedDisclosureTableRow parseRow(
            Element ownerTable,
            Element rowElement,
            int rowIndex,
            IntSupplier orderSupplier
    ) {
        HtmlSourceLineRange rowRange = locationResolver.resolve(rowElement);
        List<ParsedDisclosureTableCell> cells = new ArrayList<>();

        for (Element child : rowElement.children()) {
            String tagName = child.normalName();
            if (!"th".equals(tagName) && !"td".equals(tagName)) {
                continue;
            }
            cells.add(parseCell(
                    ownerTable,
                    child,
                    cells.size(),
                    orderSupplier
            ));
        }

        return new ParsedDisclosureTableRow(
                rowIndex,
                rowRange.startLine(),
                rowRange.endLine(),
                cells
        );
    }

    private ParsedDisclosureTableCell parseCell(
            Element ownerTable,
            Element cellElement,
            int cellIndex,
            IntSupplier orderSupplier
    ) {
        HtmlSourceLineRange cellRange = locationResolver.resolve(cellElement);

        List<ParsedDisclosureTable> nestedTables = cellElement
                .select("table")
                .stream()
                .filter(candidate -> nearestAncestorTable(candidate) == ownerTable)
                .map(candidate -> parseTable(candidate, orderSupplier))
                .toList();

        List<ParsedDisclosureImage> images = cellElement
                .select("img")
                .stream()
                .filter(candidate -> nearestAncestorTable(candidate) == ownerTable)
                .map(this::parseImage)
                .filter(Objects::nonNull)
                .toList();

        return new ParsedDisclosureTableCell(
                cellIndex,
                ParsedDisclosureTableCellType.fromXmlTag(
                        cellElement.tagName()
                ),
                parsePositiveSpan(cellElement, "rowspan"),
                parsePositiveSpan(cellElement, "colspan"),
                textExtractor.extractExcludingNestedTables(cellElement),
                cellRange.startLine(),
                cellRange.endLine(),
                nestedTables,
                images
        );
    }

    public ParsedDisclosureImage parseImage(Element imageElement) {
        String fileName = nullableAttribute(imageElement, "src");
        String caption = firstNonBlank(
                nullableAttribute(imageElement, "alt"),
                nullableAttribute(imageElement, "title")
        );
        if (fileName == null && caption == null) {
            return null;
        }

        HtmlSourceLineRange range = locationResolver.resolve(imageElement);
        return new ParsedDisclosureImage(
                fileName,
                caption,
                parseNullablePositiveInteger(imageElement.attr("width")),
                parseNullablePositiveInteger(imageElement.attr("height")),
                nullableAttribute(imageElement, "align"),
                range.startLine(),
                range.endLine()
        );
    }

    private List<Element> immediateRows(Element tableElement) {
        List<Element> rows = new ArrayList<>();
        for (Element child : tableElement.children()) {
            String tagName = child.normalName();
            if ("tr".equals(tagName)) {
                rows.add(child);
                continue;
            }
            if (!"thead".equals(tagName)
                    && !"tbody".equals(tagName)
                    && !"tfoot".equals(tagName)) {
                continue;
            }
            for (Element sectionChild : child.children()) {
                if ("tr".equals(sectionChild.normalName())) {
                    rows.add(sectionChild);
                }
            }
        }
        return rows;
    }

    private Element nearestAncestorTable(Element element) {
        Node current = element.parent();
        while (current != null) {
            if (current instanceof Element ancestor
                    && "table".equals(ancestor.normalName())) {
                return ancestor;
            }
            current = current.parent();
        }
        return null;
    }

    private int parsePositiveSpan(Element element, String attributeName) {
        String rawValue = element.attr(attributeName).strip();
        if (rawValue.isEmpty()) {
            return 1;
        }

        try {
            int value = Integer.parseInt(rawValue);
            if (value < 1) {
                throw invalidSpan(element, attributeName, rawValue);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalidSpan(element, attributeName, rawValue);
        }
    }

    private IllegalArgumentException invalidSpan(
            Element element,
            String attributeName,
            String rawValue
    ) {
        HtmlSourceLineRange range = locationResolver.resolve(element);
        return new IllegalArgumentException(
                attributeName.toUpperCase(Locale.ROOT)
                        + "은 1 이상의 정수여야 합니다. value="
                        + rawValue
                        + ", line="
                        + range.startLine()
        );
    }

    private Integer parseNullablePositiveInteger(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(rawValue.strip());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String nullableAttribute(Element element, String attributeName) {
        String value = element.attr(attributeName).strip();
        return value.isBlank() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private void requireTable(Element tableElement) {
        Objects.requireNonNull(tableElement, "tableElement는 필수입니다.");
        if (!"table".equals(tableElement.normalName())) {
            throw new IllegalArgumentException(
                    "TABLE 요소만 파싱할 수 있습니다. tag="
                            + tableElement.tagName()
            );
        }
    }
}
