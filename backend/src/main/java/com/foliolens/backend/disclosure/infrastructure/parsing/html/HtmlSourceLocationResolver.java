package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Range;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Jsoup이 추적한 문자 위치를 기존 파싱 모델이 사용하는 원문 행 번호로 변환한다.
 */
@Component
public class HtmlSourceLocationResolver {

    public HtmlSourceLineRange resolve(Node node) {
        Objects.requireNonNull(node, "node는 필수입니다.");

        Range startTagRange = node.sourceRange();
        if (!startTagRange.isTracked()) {
            return HtmlSourceLineRange.untracked();
        }

        int startLine = startTagRange.start().lineNumber();
        int endLine = startTagRange.end().lineNumber();

        if (node instanceof Element element) {
            Range endTagRange = element.endSourceRange();
            if (endTagRange.isTracked()) {
                endLine = endTagRange.end().lineNumber();
            }
        }

        return new HtmlSourceLineRange(
                startLine,
                Math.max(startLine, endLine)
        );
    }

    /**
     * 문서 내 등장 순서를 비교할 때 사용할 문자 오프셋이다.
     */
    public int startOffset(Node node) {
        Objects.requireNonNull(node, "node는 필수입니다.");
        Range range = node.sourceRange();
        return range.isTracked()
                ? range.startPos()
                : Integer.MAX_VALUE;
    }
}
