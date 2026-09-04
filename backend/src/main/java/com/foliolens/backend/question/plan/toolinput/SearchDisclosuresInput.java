package com.foliolens.backend.question.plan.toolinput;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;

import java.util.List;
import java.util.Objects;

public record SearchDisclosuresInput(List<DisclosureCategory> categories,
                                     List<String> subtypes,
                                     List<String> titleTerms,
                                     int limit) implements ToolInput {

    public SearchDisclosuresInput {
        categories = immutableCategories(categories);
        subtypes = immutableSubtypeList(subtypes);
        titleTerms = immutableTextList(titleTerms, "titleTerms");
    }

    private static List<DisclosureCategory> immutableCategories(
            List<DisclosureCategory> values
    ) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "categories에는 null 원소가 포함될 수 없습니다."
            );
        }
        return values.stream().distinct().toList();
    }

    // HCX가 자유생성한 subtypes는 "신규시설 투자등"처럼 복합명사 사이에 공백이
    // 섞여 나올 때가 있다. subtypes는 정해진 값과 정확히 일치해야 정책에 매칭되므로
    // 내부 공백까지 제거해 비교 안정성을 확보한다(titleTerms는 자유 검색어라 보존한다).
    private static List<String> immutableSubtypeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> requireText(value, "subtypes").replaceAll("\\s+", ""))
                .distinct()
                .toList();
    }

    private static List<String> immutableTextList(
            List<String> values,
            String fieldName
    ) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "에는 빈 문자열이 포함될 수 없습니다."
            );
        }
        return value.strip();
    }
}
