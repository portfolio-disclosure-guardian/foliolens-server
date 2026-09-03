package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureBlockType;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.DartHtmlDisclosureParser;
import com.foliolens.backend.disclosure.infrastructure.parsing.validation.XmlParsingValidationMetrics;
import com.foliolens.backend.disclosure.infrastructure.parsing.validation.XmlParsingValidationMetricsCollector;
import com.foliolens.backend.disclosure.infrastructure.profiling.html.HtmlStructureProfile;
import com.foliolens.backend.disclosure.infrastructure.profiling.html.HtmlStructureProfiler;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 기존 구조 조사기와 독립적으로 파싱한 결과를 비교한다. 예외가 나면 저장하지 않는다. */
@Component
public class HtmlParsingValidator {
    private final DartHtmlDisclosureParser parser;
    private final HtmlStructureProfiler profiler;
    private final XmlParsingValidationMetricsCollector metricsCollector;

    public HtmlParsingValidator(DartHtmlDisclosureParser parser, HtmlStructureProfiler profiler,
                                XmlParsingValidationMetricsCollector metricsCollector) {
        this.parser = parser;
        this.profiler = profiler;
        this.metricsCollector = metricsCollector;
    }

    public ValidatedHtmlDocument validate(Path sourceFile) {
        HtmlStructureProfile profile = profiler.profile(sourceFile);
        ParsedDisclosureDocument document = parser.parse(sourceFile);
        // 집계기는 이름만 XML이며 동일 ParsedDisclosureDocument 모델을 순회한다.
        XmlParsingValidationMetrics metrics = metricsCollector.collect(document);
        check("표", profile.tableCount(), metrics.tableCount());
        check("중첩 표", profile.nestedTableCount(), metrics.nestedTableCount());
        check("행", profile.countOf("TR"), metrics.tableRowCount());
        check("셀", profile.countOf("TH") + profile.countOf("TD"), metrics.tableCellCount());
        validateBodySections(document, metrics);
        return new ValidatedHtmlDocument(document, metrics);
    }

    private void validateBodySections(ParsedDisclosureDocument document, XmlParsingValidationMetrics metrics) {
        List<String> problems = new ArrayList<>();
        if (document.documentName() == null || document.documentName().isBlank()) problems.add("문서명 누락");
        if (document.sections().isEmpty()) problems.add("섹션 제목 미인식: 생성된 섹션이 없습니다");
        if (metrics.tableCount() == 0) problems.add("표 누락");
        if (metrics.tableCellCount() == 0) problems.add("표 셀 누락");
        if (metrics.textCharacterCount() == 0) problems.add("추출 텍스트 누락");

        int lastCorrection = -1;
        int firstBody = -1;
        boolean bodyHasTable = false;
        for (int i = 0; i < document.sections().size(); i++) {
            var section = document.sections().get(i);
            if ("정정신고(보고)".equals(section.title())) {
                lastCorrection = i;
            } else {
                if (firstBody == -1) firstBody = i;
                bodyHasTable |= section.blocks().stream().anyMatch(b -> b.type() == ParsedDisclosureBlockType.TABLE);
            }
        }
        if (lastCorrection >= 0 && firstBody == -1) {
            problems.add("정정 외 본문 섹션 누락: 정정 내용과 본문을 분리해야 합니다");
        } else if (lastCorrection >= 0 && firstBody <= lastCorrection) {
            problems.add("섹션 순서 오류: 정정 섹션 뒤에 본문 섹션이 있어야 합니다");
        }
        if (firstBody >= 0 && !bodyHasTable) problems.add("본문 섹션에 표가 없습니다");

        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("HTML 파싱 검증 실패: " + String.join("; ", problems)
                    + " [sections=" + document.sections().size()
                    + ", preambleBlocks=" + document.preambleBlocks().size()
                    + ", tables=" + metrics.tableCount() + ", rows=" + metrics.tableRowCount()
                    + ", cells=" + metrics.tableCellCount() + ", textCharacters=" + metrics.textCharacterCount() + "]");
        }
    }

    private void check(String name, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalArgumentException(name + " 구조 조사/파싱 개수 불일치: "
                    + expected + " / " + actual);
        }
    }
}
