package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.html.DecodedHtmlSource;
import com.foliolens.backend.disclosure.infrastructure.html.HtmlSourceReader;
import com.foliolens.backend.disclosure.infrastructure.parsing.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 거래소 XForms HTML 원문을 공통 파싱 모델로 변환한다.
 * 정정신고와 본문 제목을 별도 섹션으로 보존하며 정정 전 값을 현재 값으로 해석하지 않는다.
 */
@Component
public class DartHtmlDisclosureParser implements DisclosureDocumentParser {
    public static final String NAME = "DartHtmlDisclosureParser";
    public static final String VERSION = "1.1.0";
    private static final String CORRECTION_TITLE = "정정신고(보고)";
    private static final int MAX_PLAIN_SPAN_TITLE_LENGTH = 120;

    private final HtmlSourceReader sourceReader;
    private final HtmlSourceLocationResolver locations;
    private final HtmlTextExtractor texts;
    private final HtmlTableParser tables;
    private final HtmlDisclosureLinkExtractor links;

    public DartHtmlDisclosureParser(HtmlSourceReader sourceReader, HtmlSourceLocationResolver locations,
                                    HtmlTextExtractor texts, HtmlTableParser tables,
                                    HtmlDisclosureLinkExtractor links) {
        this.sourceReader = sourceReader;
        this.locations = locations;
        this.texts = texts;
        this.tables = tables;
        this.links = links;
    }

    @Override public String parserName() { return NAME; }
    @Override public String parserVersion() { return VERSION; }

    @Override
    public ParsedDisclosureDocument parse(Path sourceFile) {
        DecodedHtmlSource source = sourceReader.read(sourceFile);
        if (source.content().indexOf('\uFFFD') >= 0) {
            throw new IllegalArgumentException("HTML 원문에 디코딩 대체문자가 포함되어 있습니다.");
        }
        Document dom = Jsoup.parse(source.content(), "", Parser.htmlParser().setTrackPosition(true));
        if (dom.selectFirst("body .xforms") == null) {
            throw new IllegalArgumentException("지원하는 거래소 XForms HTML 원문이 아닙니다.");
        }
        State state = new State();
        visit(dom.body(), state);
        state.flushText();
        if (state.blockCount == 0) {
            throw new IllegalArgumentException("HTML 원문에서 본문 블록을 찾지 못했습니다.");
        }
        List<ParsedDisclosureSection> sections = state.sections.stream().map(SectionBuilder::build).toList();
        // 섹션과 문서명에 같은 제목 인식 결과를 사용한다. 정정 제목은 문서명이 아니다.
        String title = sections.stream().map(ParsedDisclosureSection::title)
                .filter(value -> value != null && !CORRECTION_TITLE.equals(value))
                .findFirst().orElse(dom.title());
        return new ParsedDisclosureDocument(source.sourceFile().getFileName().toString(), title,
                state.preamble, sections, links.extract(dom.body()));
    }

    private void visit(Node node, State state) {
        if (node instanceof TextNode text) {
            state.append(text.getWholeText(), locations.resolve(text));
            return;
        }
        if (!(node instanceof Element element) || texts.shouldIgnore(element, element.normalName())) return;
        String tag = element.normalName();
        if ("table".equals(tag)) {
            state.flushText();
            ParsedDisclosureTable table = tables.parse(element, state.tableOrder::getAndIncrement);
            state.add(ParsedDisclosureBlock.table(state.nextOrder(), table));
            return; // 하위 셀을 별도 문단으로 중복 수집하지 않는다.
        }
        if (isHeading(element)) {
            state.flushText();
            String title = texts.extract(element);
            if (title != null) {
                if (isCorrectionTitle(title)) title = CORRECTION_TITLE;
                HtmlSourceLineRange range = locations.resolve(element);
                SectionBuilder section = new SectionBuilder(state.nextOrder(), title, range);
                state.sections.add(section);
                state.current = section;
            }
            return;
        }
        if ("img".equals(tag)) {
            state.flushText();
            ParsedDisclosureImage image = tables.parseImage(element);
            if (image != null) state.add(ParsedDisclosureBlock.image(state.nextOrder(), image));
            return;
        }
        if ("br".equals(tag)) {
            state.text.append('\n');
            return;
        }
        boolean paragraphBoundary = List.of("div", "p", "li", "section", "article").contains(tag);
        if (paragraphBoundary) state.flushText();
        for (Node child : element.childNodes()) visit(child, state);
        if (paragraphBoundary) state.flushText();
    }

    private boolean isHeading(Element element) {
        // 셀의 강조 문구나 문서 바깥의 제목을 섹션으로 승격하지 않는다.
        if (element.closest(".xforms") == null || element.closest("table") != null) return false;
        if (element.hasClass("xforms_title") || element.normalName().matches("h[1-6]")) return true;
        // 정정 제목은 xforms_title이 아닌 span으로 제공된다.
        if (!"span".equals(element.normalName()) || element.selectFirst("table") != null) return false;
        String title = texts.extract(element);
        if (isCorrectionTitle(title)) return true;
        if (title == null || title.length() > MAX_PLAIN_SPAN_TITLE_LENGTH || title.contains("\n")) return false;

        // 계약 서식의 class 없는 제목: 짧은 굵은 가운데 정렬 span + 바로 뒤 본문 표.
        // 폭(width)이나 특정 공시명에는 의존하지 않는다. 숨김 span은 건너뛴다.
        String weight = styleValue(element, "font-weight");
        if (!("bold".equals(weight) || weight.matches("[7-9]00"))
                || !"center".equals(styleValue(element, "text-align"))) return false;
        Element next = element.nextElementSibling();
        while (next != null && texts.shouldIgnore(next, next.normalName())) {
            next = next.nextElementSibling();
        }
        return next != null && "table".equals(next.normalName());
    }

    private boolean isCorrectionTitle(String title) {
        return title != null && CORRECTION_TITLE.equals(title.replaceAll("\\s+", ""));
    }

    private String styleValue(Element element, String property) {
        String value = "";
        for (String declaration : element.attr("style").split(";")) {
            String[] parts = declaration.split(":", 2);
            if (parts.length == 2 && property.equalsIgnoreCase(parts[0].strip())) {
                value = parts[1].strip().toLowerCase(Locale.ROOT);
            }
        }
        return value;
    }

    private final class State {
        private int order;
        private int blockCount;
        private final AtomicInteger tableOrder = new AtomicInteger(1);
        private final List<ParsedDisclosureBlock> preamble = new ArrayList<>();
        private final List<SectionBuilder> sections = new ArrayList<>();
        private SectionBuilder current;
        private final StringBuilder text = new StringBuilder();
        private int startLine = -1;
        private int endLine = -1;

        int nextOrder() { return ++order; }

        void append(String value, HtmlSourceLineRange range) {
            text.append(value);
            if (!value.isBlank()) {
                if (startLine == -1) startLine = range.startLine();
                endLine = range.endLine();
            }
        }

        void flushText() {
            String normalized = texts.normalize(text.toString());
            if (normalized != null) {
                add(ParsedDisclosureBlock.text(ParsedDisclosureBlockType.PARAGRAPH,
                        nextOrder(), normalized, startLine, Math.max(startLine, endLine)));
            }
            text.setLength(0);
            startLine = -1;
            endLine = -1;
        }

        void add(ParsedDisclosureBlock block) {
            blockCount++;
            if (current == null) preamble.add(block);
            else {
                current.blocks.add(block);
                current.endLine = Math.max(current.endLine, block.sourceLineEnd());
            }
        }
    }

    private static final class SectionBuilder {
        private final int order;
        private final String title;
        private final int startLine;
        private int endLine;
        private final List<ParsedDisclosureBlock> blocks = new ArrayList<>();

        SectionBuilder(int order, String title, HtmlSourceLineRange range) {
            this.order = order;
            this.title = title;
            this.startLine = range.startLine();
            this.endLine = range.endLine();
        }

        ParsedDisclosureSection build() {
            return new ParsedDisclosureSection(1, order, title, startLine, endLine, blocks, List.of());
        }
    }
}
