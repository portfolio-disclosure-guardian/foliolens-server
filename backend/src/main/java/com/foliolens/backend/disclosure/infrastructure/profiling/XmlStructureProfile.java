package com.foliolens.backend.disclosure.infrastructure.profiling;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * XML 원문 파일 하나의 구조 조사 결과.
 *
 * 실제 공시 내용이나 섹션을 저장하는 객체가 아니라,
 * 태그 구성과 문서의 기본 구조를 확인하기 위한 개발용 결과 객체다.
 */
public record XmlStructureProfile(
        String fileName, // 조사한 XML 파일명
        String rootElementName, // XML의 최상위 태그
        String documentName, // DOCUMENT-NAME 태그의 내용
        long fileSizeBytes, // 파일의 크기
        int maxDepth, // XML 태그의 최대 중첩 길이
        Map<String, Long> tagCounts, // 태그 이름별 등장 횟수

        XmlAdditionalStructureProfile additionalStructure,

        long repairedAmpersandCount, // 읽기 과정에서 보정한 단독 & 개수
        long repairedLessThanCount, // 읽기 과정에서 보정한 단독 < 개수
        long repairedAttributeQuoteCount
) {

    public XmlStructureProfile {
        fileName = requireText(fileName, "fileName");

        rootElementName = requireText(rootElementName, "rootElementName");

        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException("fileSizeBytes는 0 이상이어야 합니다.");
        }

        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth는 1 이상이어야 합니다.");
        }

        if (repairedAmpersandCount < 0) {
            throw new IllegalArgumentException(
                    "repairedAmpersandCount는 0 이상이어야 합니다."
            );
        }

        if (repairedLessThanCount < 0) {
            throw new IllegalArgumentException(
                    "repairedLessThanCount는 0 이상이어야 합니다."
            );
        }

        Objects.requireNonNull(tagCounts, "tagCounts는 필수입니다.");

        /*
         * 태그 이름순으로 정렬하고 외부에서 수정할 수 없게 만든다.
         */
        tagCounts = Collections.unmodifiableMap(new TreeMap<>(tagCounts));

        additionalStructure =
                Objects.requireNonNull(
                        additionalStructure,
                        "additionalStructure는 필수입니다."
                );

        if (repairedAttributeQuoteCount < 0) {
            throw new IllegalArgumentException(
                    "repairedAttributeQuoteCount는 "
                            + "0 이상이어야 합니다."
            );
        }
    }

    /**
     * 특정 태그가 몇 번 등장했는지 반환한다.
     *
     * 조사기에서 태그명을 대문자로 정규화하므로
     * 여기에서도 대문자로 변환해 조회한다.
     */
    public long countOf(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return 0;
        }

        return tagCounts.getOrDefault(
                tagName.trim().toUpperCase(Locale.ROOT),
                0L
        );
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) 필수입니다."
            );
        }

        return value.trim();
    }
}
