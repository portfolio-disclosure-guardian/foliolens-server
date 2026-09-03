package com.foliolens.backend.disclosure.infrastructure.parsing;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.DartHtmlDisclosureParser;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 확장자가 아닌 스캔한 실제 형식과 공시 그룹/문서 역할로 선택한다. */
@Component
public class DisclosureDocumentParserRouter {
    private final DartXmlDisclosureParser xmlParser;
    private final DartHtmlDisclosureParser htmlParser;

    public DisclosureDocumentParserRouter(DartXmlDisclosureParser xmlParser, DartHtmlDisclosureParser htmlParser) {
        this.xmlParser = xmlParser;
        this.htmlParser = htmlParser;
    }

    public DisclosureDocumentParser select(DisclosureDocument document) {
        String group = document.getDisclosure().getSourceGroup().getValue();
        return switch (document.getContentFormat()) {
            case DART_XML -> {
                if (!Set.of("periodic", "major", "holding").contains(group)) {
                    throw new IllegalArgumentException("DART XML 형식과 공시 그룹이 충돌합니다: " + group);
                }
                yield xmlParser;
            }
            case HTML -> {
                if (!"exchange".equals(group) || document.getDocumentRole() == DisclosureDocumentRole.VIEWER) {
                    throw new IllegalArgumentException("PDF/HTML 뷰어 및 비거래소 HTML은 아직 지원하지 않습니다.");
                }
                yield htmlParser;
            }
            case PDF, UNKNOWN -> throw new IllegalArgumentException(
                    "지원하지 않는 원문 형식입니다: " + document.getContentFormat());
        };
    }
}
