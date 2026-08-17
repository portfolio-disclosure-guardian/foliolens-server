package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.Locale;

/**
 * DART XML 표의 셀 종류.
 * <p>
 * TH: 제목 또는 헤더 셀
 * TD: 일반 데이터 셀
 */
public enum ParsedDisclosureTableCellType {

    HEADER,
    DATA;

    /**
     * XML 태그 이름을 셀 타입으로 변환한다.
     *
     * TH -> HEADER
     * TD -> DATA
     */
    public static ParsedDisclosureTableCellType fromXmlTag(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException(
                    "표 셀 태그 이름은 필수입니다."
            );
        }

        return switch (tagName.trim().toUpperCase(Locale.ROOT)) {
            case "TH" -> HEADER;
            case "TD" -> DATA;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 표 셀 태그입니다. tagName=" + tagName
            );
        };
    }
}
