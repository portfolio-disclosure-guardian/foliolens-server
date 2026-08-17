package com.foliolens.backend.disclosure.infrastructure.profiling;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record XmlAdditionalStructureProfile(
        Map<Integer, Long> sectionLevelCounts, // SECTION-N 태그가 단계별로 몇 개 등장했는지 나타냄
        int maxSectionLevel, // XML 파일에서 발견된 가장 높은 SECTION-N의 숫자

        long nestedTableCount, // 다른 TABLE 안에서 시작된 내부 TABLE의 개수
        int maxTableDepth, // 표가 최대 몇 겹까지 중첩됐는지를 나타냄

        long paragraphInsideTitleCount, // P태그 중 몇 개가 TITLE 안에 있는가?
        long paragraphInsideTableCount, // P태그 중 몇 개가 TABLE 안에 있는가?
        long titleInsideTableCount, // TABLE 내부에서 발견된 TITLE 태그 개수

        long lineBreakTagCount, // 명시적인 줄바꿈 태그가 등장한 개수  조사 태그 예시 <BR/>, <LINEBREAK/>, <LINE-BREAK/>
        long xmlCommentCount, // XML 문법의 주석이 몇 개 존재하는지 나타냄

        Map<String, Long> imageCandidateTagCounts, // 이미지와 관련된 것으로 추정되는 태그 이름과 개수
        Map<String, Long> noteCandidateTagCounts // 각주 또는 주석과 관련된 것으로 추정되는 태그 이름과 개수
) {

    public XmlAdditionalStructureProfile {
        sectionLevelCounts = immutableSortedCopy(sectionLevelCounts);

        imageCandidateTagCounts = immutableSortedCopy(imageCandidateTagCounts);

        noteCandidateTagCounts = immutableSortedCopy(noteCandidateTagCounts);

        validateNonNegative(
                maxSectionLevel,
                "maxSectionLevel"
        );

        validateNonNegative(
                nestedTableCount,
                "nestedTableCount"
        );

        validateNonNegative(
                maxTableDepth,
                "maxTableDepth"
        );

        validateNonNegative(
                paragraphInsideTitleCount,
                "paragraphInsideTitleCount"
        );

        validateNonNegative(
                paragraphInsideTableCount,
                "paragraphInsideTableCount"
        );

        validateNonNegative(
                titleInsideTableCount,
                "titleInsideTableCount"
        );

        validateNonNegative(
                lineBreakTagCount,
                "lineBreakTagCount"
        );

        validateNonNegative(
                xmlCommentCount,
                "xmlCommentCount"
        );
    }

    public long sectionCountAbove(int level) {
        return sectionLevelCounts
                .entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey() > level
                )
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private static <K extends Comparable<? super K>> Map<K, Long> immutableSortedCopy(Map<K, Long> source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "집계 결과 Map은 필수입니다."
            );
        }

        return Collections.unmodifiableMap(new TreeMap<>(source));
    }

    private static void validateNonNegative(
            long value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "은 0 이상이어야 합니다."
            );
        }
    }
}
