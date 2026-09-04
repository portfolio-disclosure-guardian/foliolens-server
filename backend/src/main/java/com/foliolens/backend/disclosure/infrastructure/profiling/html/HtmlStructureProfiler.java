package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import com.foliolens.backend.disclosure.infrastructure.html.HtmlSourceFileValidator;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 오래된 KRX XForms HTML도 허용하는 JDK HTML 파서로 파일 구조를 조사한다.
 * HTML 전체 문자열을 DOM으로 올리지 않고 ParserCallback 이벤트로 집계한다.
 */
@Component
public class HtmlStructureProfiler {

    private static final Charset MS949 = Charset.forName("MS949");
    private static final Pattern CHARSET_PATTERN = Pattern.compile(
            "charset\\s*=\\s*['\"]?([^;\\s'\"]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final HtmlSourceFileValidator sourceFileValidator;

    public HtmlStructureProfiler(HtmlSourceFileValidator sourceFileValidator) {
        this.sourceFileValidator = Objects.requireNonNull(
                sourceFileValidator,
                "sourceFileValidator는 필수입니다."
        );
    }

    public HtmlStructureProfile profile(Path sourceFile) {
        Path normalizedFile = sourceFileValidator.validate(sourceFile);
        long fileSizeBytes = readFileSize(normalizedFile);

        try {
            return parse(normalizedFile, fileSizeBytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            if (!containsCharacterCodingException(exception)) {
                throw datasetException(normalizedFile, exception);
            }

            try {
                return parse(normalizedFile, fileSizeBytes, MS949);
            } catch (IOException fallbackException) {
                fallbackException.addSuppressed(exception);
                throw datasetException(normalizedFile, fallbackException);
            }
        }
    }

    private HtmlStructureProfile parse(
            Path sourceFile,
            long fileSizeBytes,
            Charset charset
    ) throws IOException {
        ProfilingCallback callback = new ProfilingCallback();

        try (BufferedReader reader = Files.newBufferedReader(sourceFile, charset)) {
            new ParserDelegator().parse(reader, callback, true);
        }

        return callback.toProfile(
                sourceFile.getFileName().toString(),
                charset.name(),
                fileSizeBytes
        );
    }

    private long readFileSize(Path sourceFile) {
        try {
            return Files.size(sourceFile);
        } catch (IOException exception) {
            throw datasetException(sourceFile, exception);
        }
    }

    private boolean containsCharacterCodingException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof CharacterCodingException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException datasetException(
            Path sourceFile,
            Exception exception
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                "HTML 원문 구조를 조사하지 못했습니다. path=" + sourceFile,
                exception
        );
    }

    private static final class ProfilingCallback
            extends HTMLEditorKit.ParserCallback {

        private final Map<String, Long> tagCounts = new HashMap<>();
        private final Map<String, Long> classCounts = new HashMap<>();
        private final Deque<TableState> tableStates = new ArrayDeque<>();
        private final StringBuilder titleBuffer = new StringBuilder();

        private String rootElementName;
        private String declaredCharset;
        private int currentDepth;
        private int maxDepth;
        private int titleDepth;
        private long xformsContainerCount;
        private long xformsTitleCount;
        private long xformsInputCount;
        private long tableCount;
        private long topLevelTableCount;
        private long nestedTableCount;
        private int maxTableDepth;
        private int maxRowsPerTable;
        private int maxCellsPerRow;
        private long rowSpanCellCount;
        private int maxRowSpan;
        private long colSpanCellCount;
        private int maxColSpan;
        private long invalidSpanAttributeCount;
        private long lineBreakCount;
        private long anchorCount;
        private long anchorWithHrefCount;
        private long imageCount;
        private long styleCount;
        private long scriptCount;
        private long commentCount;
        private long parserErrorCount;
        private String firstParserError;

        @Override
        public void handleStartTag(
                HTML.Tag tag,
                MutableAttributeSet attributes,
                int position
        ) {
            String name = recordElement(tag, attributes);
            currentDepth++;
            maxDepth = Math.max(maxDepth, currentDepth);

            switch (name) {
                case "TITLE" -> titleDepth++;
                case "TABLE" -> startTable();
                case "TR" -> startRow();
                case "TD", "TH" -> recordCell(attributes);
                case "A" -> recordAnchor(attributes);
                case "STYLE" -> styleCount++;
                case "SCRIPT" -> scriptCount++;
                default -> {
                }
            }
        }

        @Override
        public void handleSimpleTag(
                HTML.Tag tag,
                MutableAttributeSet attributes,
                int position
        ) {
            String name = recordElement(tag, attributes);
            switch (name) {
                case "BR" -> lineBreakCount++;
                case "IMG" -> imageCount++;
                case "A" -> recordAnchor(attributes);
                default -> {
                }
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            String name = normalizeTagName(tag);
            switch (name) {
                case "TITLE" -> titleDepth = Math.max(0, titleDepth - 1);
                case "TR" -> endRow();
                case "TABLE" -> endTable();
                default -> {
                }
            }
            currentDepth = Math.max(0, currentDepth - 1);
        }

        @Override
        public void handleText(char[] data, int position) {
            if (titleDepth > 0) {
                if (!titleBuffer.isEmpty()) {
                    titleBuffer.append(' ');
                }
                titleBuffer.append(data);
            }
        }

        @Override
        public void handleComment(char[] data, int position) {
            commentCount++;
        }

        @Override
        public void handleError(String errorMessage, int position) {
            parserErrorCount++;
            if (firstParserError == null) {
                firstParserError = normalizeError(errorMessage, position);
            }
        }

        private String recordElement(
                HTML.Tag tag,
                AttributeSet attributes
        ) {
            String name = normalizeTagName(tag);
            tagCounts.merge(name, 1L, Long::sum);
            if (rootElementName == null) {
                rootElementName = name;
            }
            recordClasses(attributes);
            if ("META".equals(name)) {
                recordDeclaredCharset(attributes);
            }
            return name;
        }

        private void recordClasses(AttributeSet attributes) {
            String classValue = attributeValue(attributes, "class");
            if (classValue == null || classValue.isBlank()) {
                return;
            }
            for (String token : classValue.strip().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                String normalized = token.toLowerCase(Locale.ROOT);
                classCounts.merge(normalized, 1L, Long::sum);
                switch (normalized) {
                    case "xforms" -> xformsContainerCount++;
                    case "xforms_title" -> xformsTitleCount++;
                    case "xforms_input" -> xformsInputCount++;
                    default -> {
                    }
                }
            }
        }

        private void recordDeclaredCharset(AttributeSet attributes) {
            if (declaredCharset != null) {
                return;
            }
            String directCharset = attributeValue(attributes, "charset");
            if (directCharset != null && !directCharset.isBlank()) {
                declaredCharset = directCharset.strip();
                return;
            }
            String content = attributeValue(attributes, "content");
            if (content == null) {
                return;
            }
            Matcher matcher = CHARSET_PATTERN.matcher(content);
            if (matcher.find()) {
                declaredCharset = matcher.group(1);
            }
        }

        private void startTable() {
            if (tableStates.isEmpty()) {
                topLevelTableCount++;
            } else {
                nestedTableCount++;
            }
            tableCount++;
            tableStates.push(new TableState());
            maxTableDepth = Math.max(maxTableDepth, tableStates.size());
        }

        private void endTable() {
            if (tableStates.isEmpty()) {
                return;
            }
            TableState state = tableStates.pop();
            state.finishRow();
            maxRowsPerTable = Math.max(maxRowsPerTable, state.rowCount);
            maxCellsPerRow = Math.max(maxCellsPerRow, state.maxCellsPerRow);
        }

        private void startRow() {
            if (!tableStates.isEmpty()) {
                tableStates.peek().startRow();
            }
        }

        private void endRow() {
            if (!tableStates.isEmpty()) {
                TableState state = tableStates.peek();
                state.finishRow();
                maxCellsPerRow = Math.max(
                        maxCellsPerRow,
                        state.maxCellsPerRow
                );
            }
        }

        private void recordCell(AttributeSet attributes) {
            if (!tableStates.isEmpty()) {
                tableStates.peek().addCell();
            }
            recordSpan(attributes, "rowspan", true);
            recordSpan(attributes, "colspan", false);
        }

        private void recordSpan(
                AttributeSet attributes,
                String attributeName,
                boolean rowSpan
        ) {
            String value = attributeValue(attributes, attributeName);
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                int parsed = Integer.parseInt(value.strip());
                if (parsed < 1) {
                    invalidSpanAttributeCount++;
                    return;
                }
                if (parsed > 1 && rowSpan) {
                    rowSpanCellCount++;
                    maxRowSpan = Math.max(maxRowSpan, parsed);
                } else if (parsed > 1) {
                    colSpanCellCount++;
                    maxColSpan = Math.max(maxColSpan, parsed);
                }
            } catch (NumberFormatException exception) {
                invalidSpanAttributeCount++;
            }
        }

        private void recordAnchor(AttributeSet attributes) {
            anchorCount++;
            String href = attributeValue(attributes, "href");
            if (href != null && !href.isBlank()) {
                anchorWithHrefCount++;
            }
        }

        private HtmlStructureProfile toProfile(
                String fileName,
                String decodedCharset,
                long fileSizeBytes
        ) {
            while (!tableStates.isEmpty()) {
                endTable();
            }
            if (rootElementName == null) {
                throw new IllegalArgumentException(
                        "HTML 원문에서 태그를 찾지 못했습니다."
                );
            }
            return new HtmlStructureProfile(
                    fileName,
                    rootElementName,
                    titleBuffer.toString(),
                    decodedCharset,
                    declaredCharset,
                    fileSizeBytes,
                    Math.max(1, maxDepth),
                    tagCounts,
                    classCounts,
                    xformsContainerCount,
                    xformsTitleCount,
                    xformsInputCount,
                    tableCount,
                    topLevelTableCount,
                    nestedTableCount,
                    maxTableDepth,
                    maxRowsPerTable,
                    maxCellsPerRow,
                    rowSpanCellCount,
                    maxRowSpan,
                    colSpanCellCount,
                    maxColSpan,
                    invalidSpanAttributeCount,
                    lineBreakCount,
                    anchorCount,
                    anchorWithHrefCount,
                    imageCount,
                    styleCount,
                    scriptCount,
                    commentCount,
                    parserErrorCount,
                    firstParserError
            );
        }

        private static String attributeValue(
                AttributeSet attributes,
                String expectedName
        ) {
            if (attributes == null) {
                return null;
            }
            var names = attributes.getAttributeNames();
            while (names.hasMoreElements()) {
                Object name = names.nextElement();
                if (expectedName.equalsIgnoreCase(String.valueOf(name))) {
                    Object value = attributes.getAttribute(name);
                    return value == null ? null : String.valueOf(value);
                }
            }
            return null;
        }

        private static String normalizeTagName(HTML.Tag tag) {
            return String.valueOf(tag).strip().toUpperCase(Locale.ROOT);
        }

        private static String normalizeError(String message, int position) {
            String normalized = message == null || message.isBlank()
                    ? "HTML parser error"
                    : message.replaceAll("\\s+", " ").strip();
            return normalized + " (position=" + position + ")";
        }

        private static final class TableState {
            private int rowCount;
            private int currentRowCells;
            private int maxCellsPerRow;
            private boolean rowOpen;

            private void startRow() {
                finishRow();
                rowOpen = true;
                currentRowCells = 0;
            }

            private void addCell() {
                if (!rowOpen) {
                    startRow();
                }
                currentRowCells++;
            }

            private void finishRow() {
                if (!rowOpen) {
                    return;
                }
                rowCount++;
                maxCellsPerRow = Math.max(maxCellsPerRow, currentRowCells);
                currentRowCells = 0;
                rowOpen = false;
            }
        }
    }
}
