package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 공백과 줄바꿈을 일정한 형태로 정리하고 bodyText, searchText를 만드는 역할만 담당
 */
@Component
public class ChunkTextNormalizer {

    /*
     * 줄바꿈을 제외한 가로 공백을 정리한다.
     * 일반 공백, 탭, 폼피드, Unicode 공간 문자를 포함한다.
     */
    private static final Pattern HORIZONTAL_WHITESPACE =
            Pattern.compile("[\\p{Zs}\\t\\f\\x0B]+");

    private static final Pattern ALL_WHITESPACE =
            Pattern.compile("\\s+");

    /**
     * 일반 문단을 정규화한다.
     *
     * 내부 줄바꿈은 보존하고,
     * 각 줄 안의 연속 공백만 하나로 줄인다.
     */
    public String normalizeParagraph(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalizedNewLines = value
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        return normalizedNewLines
                .lines()
                .map(this::normalizeLine)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    /**
     * HEADING은 한 줄 문맥으로 사용하므로
     * 모든 공백과 줄바꿈을 하나의 공백으로 만든다.
     */
    public String normalizeHeading(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return ALL_WHITESPACE
                .matcher(value)
                .replaceAll(" ")
                .strip();
    }

    /**
     * 여러 문단을 빈 줄 하나로 연결한다.
     */
    public String joinParagraphs(List<String> paragraphs) {
        Objects.requireNonNull(
                paragraphs,
                "paragraphs는 필수입니다."
        );

        return paragraphs.stream()
                .map(this::normalizeParagraph)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n\n"))
                .strip();
    }

    /**
     * Section 경로, HEADING 문맥, 본문으로
     * 실제 검색에 사용할 문자열을 만든다.
     */
    public String buildSearchText(
            String sectionPath,
            List<String> headingContexts,
            String bodyText
    ) {
        Objects.requireNonNull(
                headingContexts,
                "headingContexts는 필수입니다."
        );

        String normalizedBody =
                normalizeParagraph(bodyText);

        if (normalizedBody.isBlank()) {
            throw new IllegalArgumentException(
                    "검색 텍스트를 만들 본문이 비어 있습니다."
            );
        }

        StringBuilder result = new StringBuilder();

        if (sectionPath != null && !sectionPath.isBlank()) {
            result.append('[')
                    .append(normalizeHeading(sectionPath))
                    .append(']')
                    .append('\n');
        }

        List<String> normalizedHeadings =
                headingContexts.stream()
                        .map(this::normalizeHeading)
                        .filter(value -> !value.isBlank())
                        .toList();

        if (!normalizedHeadings.isEmpty()) {
            result.append("소제목: ")
                    .append(
                            String.join(
                                    SectionPathResolver.PATH_SEPARATOR,
                                    normalizedHeadings
                            )
                    )
                    .append('\n');
        }

        result.append(normalizedBody);

        return result.toString().strip();
    }

    private String normalizeLine(String line) {
        return HORIZONTAL_WHITESPACE
                .matcher(line)
                .replaceAll(" ")
                .strip();
    }
}