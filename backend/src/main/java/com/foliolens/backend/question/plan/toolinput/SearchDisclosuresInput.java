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
        subtypes = immutableTextList(subtypes, "subtypes");
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
