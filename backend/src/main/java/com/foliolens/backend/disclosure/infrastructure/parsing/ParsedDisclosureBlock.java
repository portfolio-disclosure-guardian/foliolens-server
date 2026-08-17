package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.Objects;

/**
 * 파싱된 본문 블록
 */
public record ParsedDisclosureBlock(
        ParsedDisclosureBlockType type, // 블럭의 종류 (HEADING, PARAGRAPH, TABLE)
        int order, // XML 안에서 등장한 순서 -> 나중에 DB에서 원문 순서를 복원하는 데 사용

        // HEADING, PARAGRAPH일 때 사용 (TABLE일 때는 null)
        String content,
        // TABLE일 때 사용 (HEADING, PARAGRAPH일 때는 null)
        ParsedDisclosureTable table,

        ParsedDisclosureImage image,

        // 시작 라인과 끝 라인은 근거 제시에 중요함!
        int sourceLineStart,
        int sourceLineEnd
) {

    public ParsedDisclosureBlock {
        type = Objects.requireNonNull(type, "type은 필수입니다.");

        if (order < 0) {
            throw new IllegalArgumentException(
                    "order는 0 이상이어야 합니다."
            );
        }

        content = normalizeNullable(content);

        switch (type) {
            case HEADING, PARAGRAPH -> {
                if (content == null) {
                    throw new IllegalArgumentException(
                            "텍스트 블록에는 content가 필수입니다."
                    );
                }

                if (table != null || image != null) {
                    throw new IllegalArgumentException(
                            "텍스트 블록은 table 또는 image를 가질 수 없습니다."
                    );
                }
            }

            case TABLE -> {
                if (table == null) {
                    throw new IllegalArgumentException(
                            "TABLE 블록에는 table이 필수입니다."
                    );
                }

                if (content != null || image != null) {
                    throw new IllegalArgumentException(
                            "TABLE 블록은 content 또는 image를 가질 수 없습니다."
                    );
                }
            }

            case IMAGE -> {
                if (image == null) {
                    throw new IllegalArgumentException(
                            "IMAGE 블록에는 image가 필수입니다."
                    );
                }

                if (content != null || table != null) {
                    throw new IllegalArgumentException(
                            "IMAGE 블록은 content 또는 table을 가질 수 없습니다."
                    );
                }
            }

            case PAGE_BREAK -> {
                if (content != null || table != null || image != null) {
                    throw new IllegalArgumentException(
                            "PAGE_BREAK 블록은 별도의 데이터를 가질 수 없습니다."
                    );
                }
            }
        }
    }

    public static ParsedDisclosureBlock text(
            ParsedDisclosureBlockType type,
            int order,
            String content,
            int sourceLineStart,
            int sourceLineEnd
    ) {

        if (type != ParsedDisclosureBlockType.HEADING
                && type != ParsedDisclosureBlockType.PARAGRAPH) {
            throw new IllegalArgumentException(
                    "text()는 HEADING 또는 PARAGRAPH만 만들 수 있습니다."
            );
        }

        return new ParsedDisclosureBlock(
                type,
                order,
                content,
                null,
                null,
                sourceLineStart,
                sourceLineEnd
        );
    }

    public static ParsedDisclosureBlock table(
            int order,
            ParsedDisclosureTable table
    ) {
        Objects.requireNonNull(table, "table은 필수입니다.");

        return new ParsedDisclosureBlock(
                ParsedDisclosureBlockType.TABLE,
                order,
                null,
                table,
                null,
                table.sourceLineStart(),
                table.sourceLineEnd()
        );
    }

    public static ParsedDisclosureBlock image(
            int order,
            ParsedDisclosureImage image
    ) {
        Objects.requireNonNull(image, "image는 필수입니다.");

        return new ParsedDisclosureBlock(
                ParsedDisclosureBlockType.IMAGE,
                order,
                null,
                null,
                image,
                image.sourceLineStart(),
                image.sourceLineEnd()
        );
    }

    public static ParsedDisclosureBlock pageBreak(int order, int sourceLine) {
        return new ParsedDisclosureBlock(
                ParsedDisclosureBlockType.PAGE_BREAK,
                order,
                null,       // content 없음
                null,  // table 없음
                null,  // image 없음
                sourceLine,
                sourceLine
        );
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
