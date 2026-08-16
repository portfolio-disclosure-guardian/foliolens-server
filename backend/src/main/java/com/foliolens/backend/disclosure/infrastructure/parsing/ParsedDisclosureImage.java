package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.Locale;

/**
 * DART XML의 IMAGE 태그를 파싱한 결과.
 *
 * 실제 이미지 바이너리를 나타내지 않는다.
 * XML에 기록된 파일명, 캡션, 표시 크기 등의 메타데이터만 보존한다.
 */
public record ParsedDisclosureImage(
        String fileName,
        String caption,
        Integer width,
        Integer height,
        String alignment,
        int sourceLineStart,
        int sourceLineEnd
) {

    public ParsedDisclosureImage {
        fileName = normalizeNullable(fileName);
        caption = normalizeCaption(caption);
        alignment = normalizeAlignment(alignment);

        if (width != null && width < 1) {
            throw new IllegalArgumentException(
                    "width는 1 이상이어야 합니다."
            );
        }

        if (height != null && height < 1) {
            throw new IllegalArgumentException(
                    "height는 1 이상이어야 합니다."
            );
        }

        validateSourceLines(sourceLineStart, sourceLineEnd);

        /*
         * 파일명이 없어도 캡션이 있다면 일부 정보는 보존할 수 있다.
         * 반대로 캡션이 없어도 파일명이 있다면 이미지가 존재했다는 사실은 보존한다.
         */
        if (fileName == null && caption == null) {
            throw new IllegalArgumentException(
                    "이미지 파일명과 캡션이 모두 비어 있습니다."
            );
        }
    }

    /**
     * 검색 결과나 화면에 간단히 표시할 문자열.
     * 캡션이 있으면 캡션을 우선하고, 없으면 파일명을 사용한다.
     */
    public String displayText() {
        return caption != null ? caption : fileName;
    }

    public boolean hasDimensions() {
        return width != null && height != null;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeCaption(String value) {
        String normalized = normalizeNullable(value);

        /*
         * 실제 데이터에 "."처럼 의미 없는 캡션이 존재할 수 있다.
         */
        if (normalized == null || ".".equals(normalized)) {
            return null;
        }

        return normalized;
    }

    private static String normalizeAlignment(String value) {
        String normalized = normalizeNullable(value);

        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private static void validateSourceLines(int start, int end) {
        if (start < -1 || end < -1) {
            throw new IllegalArgumentException(
                    "원문 행 번호는 -1 이상이어야 합니다."
            );
        }

        if (start != -1 && end != -1 && end < start) {
            throw new IllegalArgumentException(
                    "원문 종료 행은 시작 행보다 앞설 수 없습니다."
            );
        }
    }
}
