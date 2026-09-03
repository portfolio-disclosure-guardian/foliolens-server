package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HTML 표시용 공백은 정리하되 BR과 문단 경계는 줄바꿈으로 보존한다.
 */
@Component
public class HtmlTextExtractor {

    private static final Set<String> IGNORED_TAGS = Set.of(
            "script",
            "style",
            "noscript"
    );
    private static final Set<String> BLOCK_TAGS = Set.of(
            "address", "article", "aside", "blockquote", "caption",
            "dd", "div", "dl", "dt", "figcaption", "figure", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr",
            "li", "main", "nav", "ol", "p", "pre", "section", "ul"
    );

    public String extract(Element root) {
        return extract(root, false);
    }

    /**
     * 부모 셀의 텍스트와 중첩 표의 텍스트가 중복 저장되지 않도록
     * 하위 TABLE 전체를 제외하고 텍스트를 수집한다.
     */
    public String extractExcludingNestedTables(Element root) {
        return extract(root, true);
    }

    private String extract(Element root, boolean excludeNestedTables) {
        if (root == null) {
            throw new IllegalArgumentException("root는 필수입니다.");
        }

        StringBuilder result = new StringBuilder();
        for (Node child : root.childNodes()) {
            append(child, result, excludeNestedTables);
        }
        return normalize(result.toString());
    }

    private void append(
            Node node,
            StringBuilder result,
            boolean excludeNestedTables
    ) {
        if (node instanceof TextNode textNode) {
            result.append(textNode.getWholeText());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tagName = element.normalName();
        if (shouldIgnore(element, tagName)) {
            return;
        }
        if (excludeNestedTables && "table".equals(tagName)) {
            appendLineBreak(result);
            return;
        }
        if ("br".equals(tagName)) {
            appendLineBreak(result);
            return;
        }

        boolean block = BLOCK_TAGS.contains(tagName);
        if (block) {
            appendLineBreak(result);
        }
        for (Node child : element.childNodes()) {
            append(child, result, excludeNestedTables);
        }
        if (block) {
            appendLineBreak(result);
        }
    }

    boolean shouldIgnore(Element element, String tagName) {
        if (IGNORED_TAGS.contains(tagName) || element.hasClass("noprint")) {
            return true;
        }

        String compactStyle = element.attr("style")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return compactStyle.contains("display:none");
    }

    private void appendLineBreak(StringBuilder result) {
        int length = result.length();
        if (length > 0 && result.charAt(length - 1) != '\n') {
            result.append('\n');
        }
    }

    String normalize(String text) {
        String normalizedNewLines = text
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        List<String> lines = new ArrayList<>();
        for (String line : normalizedNewLines.split("\\n", -1)) {
            String normalizedLine = line
                    .replaceAll("[\\t\\f ]+", " ")
                    .strip();
            if (!normalizedLine.isBlank()) {
                lines.add(normalizedLine);
            }
        }

        return lines.isEmpty()
                ? null
                : String.join("\n", lines);
    }
}
