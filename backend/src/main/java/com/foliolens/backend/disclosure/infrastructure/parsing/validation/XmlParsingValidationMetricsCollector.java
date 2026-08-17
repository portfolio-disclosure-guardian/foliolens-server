package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import com.foliolens.backend.disclosure.infrastructure.parsing.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class XmlParsingValidationMetricsCollector {

    public XmlParsingValidationMetrics collect(ParsedDisclosureDocument document) {
        MutableMetrics metrics = new MutableMetrics();

        collectBlocks(document.preambleBlocks(), metrics);

        for (ParsedDisclosureSection section : document.sections()) {
            collectSection(section, metrics);
        }

        return metrics.toImmutable();
    }

    private void collectSection(
            ParsedDisclosureSection section,
            MutableMetrics metrics
    ) {
        metrics.sectionCount++;
        metrics.maxSectionLevel = Math.max(metrics.maxSectionLevel, section.level());

        if (section.title() != null) {
            metrics.textCharacterCount += section.title().length();
        }

        collectBlocks(section.blocks(), metrics);

        for (ParsedDisclosureSection child : section.children()) {
            collectSection(child, metrics);
        }
    }

    private void collectBlocks(
            List<ParsedDisclosureBlock> blocks,
            MutableMetrics metrics
    ) {
        for (ParsedDisclosureBlock block : blocks) {
            metrics.totalBlockCount++;

            switch (block.type()) {
                case HEADING -> {
                    metrics.headingCount++;
                    metrics.textCharacterCount += block.content().length();
                }

                case PARAGRAPH -> {
                    metrics.paragraphCount++;
                    metrics.textCharacterCount += block.content().length();
                }

                case TABLE -> collectTable(
                        block.table(),
                        false,
                        metrics
                );

                case IMAGE -> {
                    metrics.imageCount++;
                    countImageText(block.image(), metrics);
                }

                case PAGE_BREAK ->
                        metrics.pageBreakCount++;
            }
        }
    }

    private void collectTable(
            ParsedDisclosureTable table,
            boolean nested,
            MutableMetrics metrics
    ) {
        metrics.tableCount++;

        if (nested) {
            metrics.nestedTableCount++;
        }

        metrics.tableRowCount += table.rows().size();

        for (ParsedDisclosureTableRow row : table.rows()) {
            metrics.tableCellCount += row.cells().size();

            for (ParsedDisclosureTableCell cell : row.cells()) {
                if (cell.text() != null) {
                    metrics.textCharacterCount += cell.text().length();
                }

                for (ParsedDisclosureImage image : cell.images()) {
                    metrics.imageCount++;
                    countImageText(image, metrics);
                }

                for (ParsedDisclosureTable nestedTable
                        : cell.nestedTables()) {

                    collectTable(
                            nestedTable,
                            true,
                            metrics
                    );
                }
            }
        }
    }

    private void countImageText(
            ParsedDisclosureImage image,
            MutableMetrics metrics
    ) {
        if (image.caption() != null) {
            metrics.textCharacterCount += image.caption().length();
        }
    }

    private static final class MutableMetrics {

        private int sectionCount;
        private int maxSectionLevel;

        private int totalBlockCount;
        private int headingCount;
        private int paragraphCount;
        private int pageBreakCount;

        private int tableCount;
        private int nestedTableCount;
        private int tableRowCount;
        private int tableCellCount;

        private int imageCount;
        private long textCharacterCount;

        private XmlParsingValidationMetrics toImmutable() {
            return new XmlParsingValidationMetrics(
                    sectionCount,
                    maxSectionLevel,
                    totalBlockCount,
                    headingCount,
                    paragraphCount,
                    pageBreakCount,
                    tableCount,
                    nestedTableCount,
                    tableRowCount,
                    tableCellCount,
                    imageCount,
                    textCharacterCount
            );
        }
    }
}
