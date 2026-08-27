package com.foliolens.backend.disclosure.infrastructure.parsing;

/**
 * 중첩 표가 부모 셀 안에서 등장한 위치의 인접 텍스트 문맥이다.
 *
 * precedingText는 중첩 TABLE 시작 직전의 직접 텍스트이고,
 * followingText는 중첩 TABLE 종료 직후의 직접 텍스트다.
 * 최상위 표에는 이 문맥을 연결하지 않는다.
 */
public record ParsedDisclosureTableContext(
        String precedingText,
        String followingText
) {

    public ParsedDisclosureTableContext {
        precedingText = normalizeNullableText(precedingText);
        followingText = normalizeNullableText(followingText);
    }

    public boolean hasPrecedingText() {
        return precedingText != null;
    }

    public boolean hasFollowingText() {
        return followingText != null;
    }

    public boolean isEmpty() {
        return precedingText == null && followingText == null;
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        /*
         * 운영체제별 개행 문자를 \n으로 통일하되 문맥 안의
         * 문단·줄 경계는 이후 문장 선택에 필요하므로 보존한다.
         */
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();

        return normalized.isBlank()
                ? null
                : normalized;
    }
}
