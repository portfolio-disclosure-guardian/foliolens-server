package com.foliolens.backend.disclosure.domain;

import lombok.Getter;

import java.util.Arrays;

/**
 * 원본 manifest의 값을 서비스 카테고리와 연결
 */
@Getter
public enum DisclosureSourceGroup {
    PERIODIC("periodic", DisclosureCategory.PERIODIC),
    MAJOR("major", DisclosureCategory.MATERIAL),
    EXCHANGE("exchange", DisclosureCategory.EXCHANGE),
    HOLDING("holding", DisclosureCategory.OWNERSHIP);

    private final String value;
    private final DisclosureCategory category;

    DisclosureSourceGroup(String value, DisclosureCategory category) {
        this.value = value;
        this.category = category;
    }

    public static DisclosureSourceGroup fromValue(String value) {
        return Arrays.stream(values())
                .filter(group -> group.value.equals(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("지원하지 않는 공시 그룹입니다: " + value)
                );
    }
}
