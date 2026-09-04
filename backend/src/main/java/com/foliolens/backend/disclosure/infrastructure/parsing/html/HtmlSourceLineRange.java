package com.foliolens.backend.disclosure.infrastructure.parsing.html;

/**
 * HTML 노드가 원문에서 차지하는 행 범위.
 * Jsoup 위치 추적이 꺼져 있거나 위치를 알 수 없으면 -1을 사용한다.
 */
public record HtmlSourceLineRange(
        int startLine,
        int endLine
) {

    private static final HtmlSourceLineRange UNTRACKED =
            new HtmlSourceLineRange(-1, -1);

    public HtmlSourceLineRange {
        if (startLine < -1 || endLine < -1) {
            throw new IllegalArgumentException(
                    "원문 행 번호는 -1 이상이어야 합니다."
            );
        }
        if (startLine != -1 && endLine != -1 && endLine < startLine) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }

    public static HtmlSourceLineRange untracked() {
        return UNTRACKED;
    }

    public boolean isTracked() {
        return startLine != -1;
    }
}
